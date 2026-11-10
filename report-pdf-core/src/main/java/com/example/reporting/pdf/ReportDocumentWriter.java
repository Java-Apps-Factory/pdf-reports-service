package com.example.reporting.pdf;

import com.example.reporting.pdf.table.CellValue;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Streaming writer for one tabular report PDF (SPEC section 10).
 *
 * <p>Pass 1 lays out sections and rows as they arrive, keeping page content in temp files rather than heap.
 * {@link #finish} runs pass 2: it draws the summary into the area reserved on page 1, stamps headers, footers
 * and "Page X of Y", adds bookmarks, and saves. Call order per section is {@link #beginSection}, then any
 * number of {@link #addRow} calls or one {@link #addEmptyMessage}, then {@link #endSection}.
 *
 * <p>A writer is single-threaded and single-use. Always {@link #close()} it, also after {@link #finish}.
 */
public interface ReportDocumentWriter extends AutoCloseable {

    /**
     * Starts a category section: heading, bookmark, table header row. The heading is placed when the first row
     * or the empty message arrives, so that it is kept together with them.
     *
     * @param section the section heading, continuation label and table columns
     * @throws IOException if writing page content fails
     * @throws IllegalStateException if a section is already open or the writer is finished
     */
    void beginSection(SectionSpec section) throws IOException;

    /**
     * Adds one data row; cells in column order. Never splits a row across pages: a row that does not fit
     * starts a continuation page with the continuation label and a repeated header row. A row taller than a
     * whole page is cut and its last visible line ends in an ellipsis.
     *
     * @param cells formatted cells, one per column
     * @throws IOException if writing page content fails
     * @throws IllegalArgumentException if the cell count differs from the column count
     * @throws IllegalStateException if no section is open or the empty message was already added
     */
    void addRow(List<CellValue> cells) throws IOException;

    /**
     * Renders the empty-category message under the table header. Allowed once per section, and only when the
     * section has no rows.
     *
     * @param message the message, for example "No content to display in this category."
     * @throws IOException if writing page content fails
     * @throws IllegalStateException if no section is open, it already has rows, or it already has a message
     */
    void addEmptyMessage(String message) throws IOException;

    /**
     * Ends the current section.
     *
     * @throws IOException if writing page content fails
     * @throws IllegalStateException if no section is open
     */
    void endSection() throws IOException;

    /**
     * Draws the summary into the area reserved on page 1, runs pass 2 and saves the PDF to {@code out}.
     *
     * @param summary summary rows; at most {@link DocumentSpec#summaryRowCount()} rows
     * @param out destination; not closed by this method
     * @return page count, rows written and the first page of each section
     * @throws IOException if decorating or saving fails
     * @throws IllegalStateException if a section is still open or the writer is already finished
     */
    RenderResult finish(SummaryData summary, OutputStream out) throws IOException;

    /** Releases PDFBox resources and temp files. Safe to call more than once and after {@link #finish}. */
    @Override
    void close();
}
