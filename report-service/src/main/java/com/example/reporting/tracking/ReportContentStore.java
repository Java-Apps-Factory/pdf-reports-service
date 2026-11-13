package com.example.reporting.tracking;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
