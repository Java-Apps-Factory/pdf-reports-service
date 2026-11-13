package com.example.reporting.rendering;

import com.example.reporting.RenderingException;
import com.example.reporting.ReportDataException;
import com.example.reporting.TransientGenerationException;
import com.example.reporting.data.ReferenceDataRepository;
import com.example.reporting.data.ReportCategory;
import com.example.reporting.data.ReportDataProvider;
import com.example.reporting.data.ReportRow;
import com.example.reporting.pdf.RenderResult;
import com.example.reporting.pdf.ReportDocumentWriter;
import com.example.reporting.pdf.ReportDocumentWriters;
import com.example.reporting.pdf.table.CellValue;
import com.example.reporting.profile.ColumnDefinition;
import com.example.reporting.profile.ProfileRegistry;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.profile.StateProfile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renders the report of one state and FIPS code (Template Method, SPEC section 8 stage B).
 *
 * <p>{@link #compose} fixes the document skeleton: title block and reserved summary, then every active
 * category in display order (its rows, or the empty message), then the summary and pass 2. Rows stream from
 * {@link ReportDataProvider} straight into the writer; categories without rows are emitted when the stream
 * passes their position, and the rest after the stream ends. {@link SummaryCollector} counts the same rows
 * (AD-05). Subclasses may add state-specific content through the protected hooks.
 *
 * <p>Errors: unknown FIPS code or category → {@link ReportDataException}; a full temp disk or an interrupted
 * thread → {@link TransientGenerationException}; any other PDF error → {@link RenderingException}. Database
 * exceptions propagate unchanged for the caller to classify.
 */
@Component
public class FipsReportComposer {

    private final ProfileRegistry profiles;
    private final ReferenceDataRepository referenceData;
    private final ReportDataProvider dataProvider;
    private final DocumentSpecFactory specFactory;
    private final Path workDir;

    /**
     * Creates the composer.
     *
     * @param profiles validated state profiles
     * @param referenceData reference data reader
     * @param dataProvider source of report rows
     * @param specFactory builds the PDF specs
     * @param properties reporting configuration (temp directory)
     */
    public FipsReportComposer(ProfileRegistry profiles, ReferenceDataRepository referenceData,
            ReportDataProvider dataProvider, DocumentSpecFactory specFactory, ReportingProperties properties) {
        this.profiles = profiles;
        this.referenceData = referenceData;
        this.dataProvider = dataProvider;
        this.specFactory = specFactory;
        this.workDir = properties.getGeneration().getTempDir();
    }

    /**
     * Renders the report and writes the PDF to {@code out}.
     *
     * @param request state, FIPS code, ids and data-as-of time
     * @param out destination of the PDF; not closed
     * @return page count, rows written and section pages
     */
    public final RenderResult compose(ReportRequest request, OutputStream out) {
        String state = request.stateCode();
        StateProfile profile = profiles.profile(state)
                .orElseThrow(() -> new ReportDataException("No profile configured for state " + state));
        String locality = referenceData.localityName(state, request.fipsCode())
                .orElseThrow(() -> new ReportDataException("FIPS code not in RPT_FIPS for state"));
        List<ReportCategory> categories = referenceData.activeCategories(state);
        if (categories.isEmpty()) {
            throw new ReportDataException("State " + state + " has no active categories");
        }
        SummaryCollector collector = new SummaryCollector(categories);
        try (ReportDocumentWriter writer = ReportDocumentWriters.open(
                specFactory.documentSpec(request, profile, profiles.logo(state), locality, categories.size()),
                workDir)) {
            beforeCategories(writer, request);
            Sections sections = new Sections(writer, profile, categories, collector, state);
            dataProvider.streamRows(state, request.fipsCode(), sections::accept);
            sections.finish();
            afterCategories(writer, request);
            RenderResult result = writer.finish(collector.toSummary(DocumentSpecFactory::categoryLabel), out);
            if (result.rowsWritten() != collector.total()) {
                throw new RenderingException("Rendered " + result.rowsWritten() + " rows but counted "
                        + collector.total(), null);
            }
            return result;
        } catch (IOException e) {
            throw classify(e);
        } catch (UncheckedIOException e) {
            throw classify(e.getCause());
        }
    }

    /**
     * Hook before the first category section; default does nothing.
     *
     * @param writer the open writer
     * @param request what is rendered
     * @throws IOException if writing fails
     */
    protected void beforeCategories(ReportDocumentWriter writer, ReportRequest request) throws IOException {
    }

    /**
     * Hook after the last category section; default does nothing.
     *
     * @param writer the open writer
     * @param request what is rendered
     * @throws IOException if writing fails
     */
    protected void afterCategories(ReportDocumentWriter writer, ReportRequest request) throws IOException {
    }

    /**
     * Turns one row into cells, in column order.
     *
     * @param row the source row
     * @param columns the category's columns
     * @param formatters one formatter per column
     * @return formatted cells
     */
    protected List<CellValue> cells(ReportRow row, List<ColumnDefinition> columns, List<CellFormatter> formatters) {
        List<CellValue> cells = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            Object value = row.value(columns.get(i).getKey()).orElse(null);
            cells.add(CellValue.of(formatters.get(i).format(value)));
        }
        return cells;
    }

    private static RuntimeException classify(IOException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("No space left on device") || message.contains("There is not enough space")) {
            return new TransientGenerationException("Temp disk full while rendering", e);
        }
        return new RenderingException("PDF rendering failed: " + e.getClass().getSimpleName(), e);
    }

    /** Emits category sections in display order as the row stream passes them. */
    private final class Sections {
        private final ReportDocumentWriter writer;
        private final StateProfile profile;
        private final List<ReportCategory> categories;
        private final Map<String, Integer> indexByCode = new HashMap<>();
        private final SummaryCollector collector;
        private final String state;
        private int current = -1;
        private boolean open;
        private List<ColumnDefinition> columns;
        private List<CellFormatter> formatters;

        Sections(ReportDocumentWriter writer, StateProfile profile, List<ReportCategory> categories,
                SummaryCollector collector, String state) {
            this.writer = writer;
            this.profile = profile;
            this.categories = categories;
            this.collector = collector;
            this.state = state;
            for (int i = 0; i < categories.size(); i++) {
                indexByCode.put(categories.get(i).code(), i);
            }
        }

        void accept(ReportRow row) {
            if (Thread.currentThread().isInterrupted()) {
                throw new TransientGenerationException("Generation interrupted", null);
            }
            Integer index = indexByCode.get(row.categoryCode());
            if (index == null || row.displayOrder().isEmpty()) {
                throw new ReportDataException("Record with category " + row.categoryCode()
                        + " that is not an active category of state " + state);
            }
            if (index < current) {
                throw new RenderingException("Rows are not in category display order", null);
            }
            try {
                advanceTo(index);
                writer.addRow(cells(row, columns, formatters));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            collector.add(row.categoryCode());
        }

        void finish() throws IOException {
            closeOpen();
            for (int i = current + 1; i < categories.size(); i++) {
                emitEmpty(i);
            }
            current = categories.size();
        }

        private void advanceTo(int index) throws IOException {
            if (index == current) {
                return;
            }
            closeOpen();
            for (int i = current + 1; i < index; i++) {
                emitEmpty(i);
            }
            ReportCategory category = categories.get(index);
            writer.beginSection(specFactory.sectionSpec(profile, category));
            columns = profile.columnsFor(category.code());
            formatters = new ArrayList<>(columns.size());
            for (ColumnDefinition column : columns) {
                formatters.add(CellFormatters.forType(column.getType()));
            }
            current = index;
            open = true;
        }

        private void emitEmpty(int index) throws IOException {
            writer.beginSection(specFactory.sectionSpec(profile, categories.get(index)));
            writer.addEmptyMessage(profile.getEmptyCategoryMessage());
            writer.endSection();
        }

        private void closeOpen() throws IOException {
            if (open) {
                writer.endSection();
                open = false;
            }
        }
    }
}
