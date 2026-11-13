package com.example.reporting.tracking;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The claim protocol and every status change after creation (SPEC section 9), in plain JDBC that runs
 * unchanged on H2 and DB2. Each UPDATE is guarded by {@code VERSION}, so two nodes can never both act on the
 * same document: one row updated means this node won, zero means another node did.
 */
@Repository
public class DocumentClaimRepository {

    /** Longest stored error text. */
    public static final int MAX_ERROR_LENGTH = 2000;

    private static final String CANDIDATES = "SELECT DOC_ID, VERSION FROM REPORT_DOCUMENT "
            + "WHERE STATUS IN (?, ?) AND (NEXT_ATTEMPT_AT IS NULL OR NEXT_ATTEMPT_AT <= ?) "
            + "ORDER BY CREATED_AT, DOC_ID FETCH FIRST ? ROWS ONLY";
    private static final String CLAIM_GENERATION = "UPDATE REPORT_DOCUMENT SET STATUS = ?, LEASE_OWNER = ?, "
            + "LEASE_EXPIRES_AT = ?, GENERATION_ATTEMPTS = GENERATION_ATTEMPTS + 1, VERSION = VERSION + 1, "
            + "UPDATED_AT = ? WHERE DOC_ID = ? AND VERSION = ?";
    private static final String MARK_GENERATED = "UPDATE REPORT_DOCUMENT SET STATUS = ?, RECORD_COUNT = ?, "
            + "PAGE_COUNT = ?, SIZE_BYTES = ?, SHA256 = ?, GENERATED_AT = ?, NEXT_ATTEMPT_AT = NULL, "
            + "LAST_ERROR = NULL, LEASE_OWNER = NULL, LEASE_EXPIRES_AT = NULL, VERSION = VERSION + 1, "
            + "UPDATED_AT = ? WHERE DOC_ID = ? AND VERSION = ?";
    private static final String MARK_FAILED = "UPDATE REPORT_DOCUMENT SET STATUS = ?, NEXT_ATTEMPT_AT = ?, "
            + "LAST_ERROR = ?, LEASE_OWNER = NULL, LEASE_EXPIRES_AT = NULL, VERSION = VERSION + 1, "
            + "UPDATED_AT = ? WHERE DOC_ID = ? AND VERSION = ?";
    private static final String EXPIRED = "SELECT DOC_ID, VERSION, STATUS, GENERATION_ATTEMPTS, LEASE_OWNER "
            + "FROM REPORT_DOCUMENT WHERE STATUS IN (?, ?) AND LEASE_EXPIRES_AT < ? ORDER BY DOC_ID";

    private final JdbcTemplate jdbc;

    /**
     * Creates the repository.
     *
     * @param jdbc application JdbcTemplate
     */
    public DocumentClaimRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims up to {@code limit} documents that are PENDING or due GENERATION_FAILED, oldest first, moving each
     * to GENERATING with a lease and counting one generation attempt.
     *
     * @param limit maximum documents to claim (free worker threads)
     * @param owner this node's ID
     * @param now database time of this poll
     * @param leaseUntil database time + generation lease
     * @return the documents this node won
     */
    public List<Claim> claimForGeneration(int limit, String owner, Instant now, Instant leaseUntil) {
        if (limit <= 0) {
            return List.of();
        }
        List<Claim> candidates = jdbc.query(CANDIDATES, (rs, n) -> new Claim(rs.getLong(1), rs.getInt(2)),
                DocumentStatus.PENDING.name(), DocumentStatus.GENERATION_FAILED.name(), ts(now), limit);
        List<Claim> won = new ArrayList<>(candidates.size());
        for (Claim candidate : candidates) {
            int updated = jdbc.update(CLAIM_GENERATION, DocumentStatus.GENERATING.name(), owner, ts(leaseUntil),
                    ts(now), candidate.docId(), candidate.version());
            if (updated == 1) {
                won.add(new Claim(candidate.docId(), candidate.version() + 1));
            }
        }
        return won;
    }

    /**
     * Moves a claimed document to GENERATED, records the PDF facts and clears the lease.
     *
     * @param claim the claim held by this node
     * @param facts record count, page count, size and SHA-256 of the stored PDF
     * @param now database time
     * @return true if this node still held the claim; false if the lease was lost
     */
    public boolean markGenerated(Claim claim, GeneratedFacts facts, Instant now) {
        DocumentStatus.GENERATING.requireTransitionTo(DocumentStatus.GENERATED);
        return jdbc.update(MARK_GENERATED, DocumentStatus.GENERATED.name(), facts.recordCount(), facts.pageCount(),
                facts.sizeBytes(), facts.sha256(), ts(now), ts(now), claim.docId(), claim.version()) == 1;
    }

    /**
     * Moves a claimed GENERATING document to a failure status and clears the lease.
     *
     * @param claim the claim held by this node
     * @param target DATA_ERROR, GENERATION_FAILED or GENERATION_FAILED_FINAL
     * @param nextAttemptAt when to retry; {@code null} for final statuses
     * @param error error text without record values; truncated to 2,000 characters
     * @param now database time
     * @return true if this node still held the claim
     */
    public boolean markGenerationFailed(Claim claim, DocumentStatus target, Instant nextAttemptAt, String error,
            Instant now) {
        DocumentStatus.GENERATING.requireTransitionTo(target);
        if (target == DocumentStatus.GENERATED) {
            throw new IllegalArgumentException("Use markGenerated");
        }
        return jdbc.update(MARK_FAILED, target.name(), ts(nextAttemptAt), truncate(error), ts(now), claim.docId(),
                claim.version()) == 1;
    }

    /**
     * Finds GENERATING and SENDING documents whose lease expired before {@code now}.
     *
     * @param now database time
     * @return expired leases, by document ID
     */
    public List<ExpiredLease> findExpiredLeases(Instant now) {
        return jdbc.query(EXPIRED, (rs, n) -> new ExpiredLease(rs.getLong(1), rs.getInt(2),
                DocumentStatus.valueOf(rs.getString(3).trim()), rs.getInt(4), rs.getString(5)),
                DocumentStatus.GENERATING.name(), DocumentStatus.SENDING.name(), ts(now));
    }

    /**
     * Applies a lease-expired transition (SPEC section 9), guarded by the version seen when the lease was found.
     *
     * @param lease the expired lease
     * @param target GENERATION_FAILED, GENERATION_FAILED_FINAL or SEND_FAILED
     * @param nextAttemptAt when to retry; {@code null} for final statuses
     * @param error error text
     * @param now database time
     * @return true if the document was still in the expired state
     */
    public boolean recoverLease(ExpiredLease lease, DocumentStatus target, Instant nextAttemptAt, String error,
            Instant now) {
        lease.status().requireTransitionTo(target);
        return jdbc.update(MARK_FAILED, target.name(), ts(nextAttemptAt), truncate(error), ts(now), lease.docId(),
                lease.version()) == 1;
    }

    /**
     * Cuts an error text to the column size.
     *
     * @param error text, may be {@code null}
     * @return at most 2,000 characters, or {@code null}
     */
    public static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /** A document this node claimed, with the version its next UPDATE must match. */
    public static final class Claim {
        private final long docId;
        private final int version;

        /**
         * Creates the claim.
         *
         * @param docId document ID
         * @param version current version
         */
        public Claim(long docId, int version) {
            this.docId = docId;
            this.version = version;
        }

        public long docId() {
            return docId;
        }

        public int version() {
            return version;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Claim && ((Claim) other).docId == docId && ((Claim) other).version == version;
        }

        @Override
        public int hashCode() {
            return Objects.hash(docId, version);
        }

        @Override
        public String toString() {
            return "Claim[doc=" + docId + ", v=" + version + "]";
        }
    }

    /** Facts about a stored PDF. */
    public static final class GeneratedFacts {
        private final long recordCount;
        private final int pageCount;
        private final long sizeBytes;
        private final String sha256;

        /**
         * Creates the facts.
         *
         * @param recordCount rows rendered
         * @param pageCount pages
         * @param sizeBytes PDF size
         * @param sha256 hex SHA-256 of the PDF
         */
        public GeneratedFacts(long recordCount, int pageCount, long sizeBytes, String sha256) {
            this.recordCount = recordCount;
            this.pageCount = pageCount;
            this.sizeBytes = sizeBytes;
            this.sha256 = Objects.requireNonNull(sha256, "sha256");
        }

        public long recordCount() {
            return recordCount;
        }

        public int pageCount() {
            return pageCount;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public String sha256() {
            return sha256;
        }
    }

    /** A GENERATING or SENDING document whose lease expired. */
    public static final class ExpiredLease {
        private final long docId;
        private final int version;
        private final DocumentStatus status;
        private final int generationAttempts;
        private final String owner;

        /**
         * Creates the value.
         *
         * @param docId document ID
         * @param version version when found
         * @param status GENERATING or SENDING
         * @param generationAttempts attempts used so far
         * @param owner node that held the lease
         */
        public ExpiredLease(long docId, int version, DocumentStatus status, int generationAttempts, String owner) {
            this.docId = docId;
            this.version = version;
            this.status = Objects.requireNonNull(status, "status");
            this.generationAttempts = generationAttempts;
            this.owner = owner;
        }

        public long docId() {
            return docId;
        }

        public int version() {
            return version;
        }

        public DocumentStatus status() {
            return status;
        }

        public int generationAttempts() {
            return generationAttempts;
        }

        public String owner() {
            return owner;
        }
    }
}
