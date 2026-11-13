package com.example.reporting.pdf.table;

/** How a table row is tagged. Internal to report-pdf-core. */
public enum RowKind {
    /** First header row of a table: header cells with column scope. */
    HEADER,
    /** Data row: data cells. */
    DATA,
    /** Total row: a header cell with row scope, then data cells. */
    TOTAL,
    /** Header row repeated on a continuation page: a pagination artifact. */
    REPEATED_HEADER
}
