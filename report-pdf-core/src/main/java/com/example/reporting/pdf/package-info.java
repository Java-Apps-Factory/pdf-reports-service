/**
 * Streaming, two-pass tabular PDF report engine over PDFBox 3.0 (SPEC section 10).
 *
 * <p>This module must not depend on Spring; it imports only PDFBox and the JDK. The supported API is
 * {@link com.example.reporting.pdf.ReportDocumentWriters}, {@link com.example.reporting.pdf.ReportDocumentWriter}
 * and the value objects it takes and returns: {@code DocumentSpec}, {@code SectionSpec}, {@code SummaryData},
 * {@code RenderResult}, {@code table.TableSpec}, {@code table.ColumnSpec}, {@code table.Alignment},
 * {@code table.CellValue}, {@code text.FontSet}, {@code layout.PageGeometry}, {@code layout.Margins} and
 * {@code a11y.TaggingContext}. Other public classes in the sub-packages are public only because Java has no
 * cross-package visibility; their Javadoc marks them "Internal to report-pdf-core".
 */
package com.example.reporting.pdf;
