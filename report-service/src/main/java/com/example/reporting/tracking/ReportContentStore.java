package com.example.reporting.tracking;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Stores PDFs in {@code REPORT_CONTENT} (AD-07) by streaming them with JDBC {@code setBinaryStream} and
 * {@code getBinaryStream}; never mapped by JPA, never read by status queries.
 */
@Repository
public class ReportContentStore {

    private static final String DELETE = "DELETE FROM REPORT_CONTENT WHERE DOC_ID = ?";
    private static final String INSERT = "INSERT INTO REPORT_CONTENT (DOC_ID, PDF) VALUES (?, ?)";
    private static final String SELECT = "SELECT PDF FROM REPORT_CONTENT WHERE DOC_ID = ?";
    private static final String PURGEABLE = "SELECT d.DOC_ID FROM REPORT_DOCUMENT d "
            + "WHERE d.CONTENT_PURGED_AT IS NULL AND ((d.STATUS = ? AND d.SENT_AT < ?) "
            + "OR (d.STATUS = ? AND d.UPDATED_AT < ?)) "
            + "AND EXISTS (SELECT 1 FROM REPORT_CONTENT c WHERE c.DOC_ID = d.DOC_ID) "
            + "ORDER BY d.DOC_ID FETCH FIRST ? ROWS ONLY";
    private static final String MARK_PURGED =
            "UPDATE REPORT_DOCUMENT SET CONTENT_PURGED_AT = ?, UPDATED_AT = ?, VERSION = VERSION + 1 WHERE DOC_ID = ?";

    private final JdbcTemplate jdbc;

    /**
     * Creates the store.
     *
     * @param jdbc application JdbcTemplate
     */
    public ReportContentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Streams a finished PDF file into the database, replacing any earlier content of the document. Call it
     * inside the transaction that marks the document GENERATED.
     *
     * @param docId document ID
     * @param pdf the PDF file
     */
    public void store(long docId, Path pdf) {
        try {
            long size = Files.size(pdf);
            jdbc.update(DELETE, docId);
            try (InputStream in = Files.newInputStream(pdf)) {
                jdbc.update(INSERT, ps -> {
                    ps.setLong(1, docId);
                    ps.setBinaryStream(2, in, size);
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read PDF of document " + docId, e);
        }
    }

    /**
     * Streams the stored PDF to {@code out}.
     *
     * @param docId document ID
     * @param out destination; not closed
     * @return true if content existed and was copied
     */
    public boolean copyTo(long docId, OutputStream out) {
        Boolean found = jdbc.query(SELECT, rs -> {
            if (!rs.next()) {
                return false;
            }
            try (InputStream in = rs.getBinaryStream(1)) {
                in.transferTo(out);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot stream PDF of document " + docId, e);
            }
            return true;
        }, docId);
        return Boolean.TRUE.equals(found);
    }

    /**
     * Loads the stored PDF into memory for sending. Only a send holds a whole PDF in heap (NFR-02).
     *
     * @param docId document ID
     * @return the bytes, or empty if no content is stored
     */
    public Optional<byte[]> load(long docId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return copyTo(docId, out) ? Optional.of(out.toByteArray()) : Optional.empty();
    }

    /**
     * Finds documents whose content may be purged (FR-18): SENT before {@code cutoff}, or SUPERSEDED (never to be
     * sent) and last changed before {@code cutoff}, with content still stored.
     *
     * @param cutoff database time minus the retention period
     * @param limit maximum IDs to return
     * @return document IDs
     */
    public List<Long> findPurgeable(Instant cutoff, int limit) {
        return jdbc.queryForList(PURGEABLE, Long.class, DocumentStatus.SENT.name(), Timestamp.from(cutoff),
                DocumentStatus.SUPERSEDED.name(), Timestamp.from(cutoff), limit);
    }

    /**
     * Deletes the content of the documents and sets {@code CONTENT_PURGED_AT}; call inside one transaction.
     *
     * @param docIds documents to purge
     * @param now database time
     */
    public void purge(List<Long> docIds, Instant now) {
        Timestamp at = Timestamp.from(now);
        List<Object[]> deletes = new ArrayList<>(docIds.size());
        List<Object[]> marks = new ArrayList<>(docIds.size());
        for (Long docId : docIds) {
            deletes.add(new Object[] {docId});
            marks.add(new Object[] {at, at, docId});
        }
        jdbc.batchUpdate(DELETE, deletes);
        jdbc.batchUpdate(MARK_PURGED, marks);
    }

    /**
     * Tells whether content is stored for a document.
     *
     * @param docId document ID
     * @return true if a row exists
     */
    public boolean exists(long docId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM REPORT_CONTENT WHERE DOC_ID = ?", Integer.class,
                docId);
        return count != null && count > 0;
    }
}
