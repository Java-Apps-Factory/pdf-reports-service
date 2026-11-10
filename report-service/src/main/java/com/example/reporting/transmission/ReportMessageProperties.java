package com.example.reporting.transmission;

/**
 * JMS property names of the report message contract, version 1 (SPEC section 13).
 *
 * <p>Properties carry metadata only; names and other record data never appear here (NFR-13).
 */
public final class ReportMessageProperties {

    /** Idempotency key (long); downstream de-duplicates on it. */
    public static final String REPORT_DOC_ID = "reportDocId";
    /** Run that produced the document (long). */
    public static final String RUN_ID = "runId";
    /** Two-letter state code (String). */
    public static final String STATE_CODE = "stateCode";
    /** Five-digit FIPS code (String). */
    public static final String FIPS_CODE = "fipsCode";
    /** When the PDF was created (String, ISO-8601 with offset). */
    public static final String GENERATED_AT = "generatedAt";
    /** Data snapshot time printed on the report (String, ISO-8601 with offset). */
    public static final String DATA_AS_OF = "dataAsOf";
    /** Rows in the report (int). */
    public static final String RECORD_COUNT = "recordCount";
    /** Pages in the report (int). */
    public static final String PAGE_COUNT = "pageCount";
    /** Hex SHA-256 of the message body (String). */
    public static final String SHA256 = "sha256";
    /** Body media type (String). */
    public static final String CONTENT_TYPE = "contentType";
    /** Contract version (int). */
    public static final String SCHEMA_VERSION = "schemaVersion";

    /** Value of {@link #CONTENT_TYPE}. */
    public static final String CONTENT_TYPE_PDF = "application/pdf";
    /** Current value of {@link #SCHEMA_VERSION}. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private ReportMessageProperties() {
    }
}
