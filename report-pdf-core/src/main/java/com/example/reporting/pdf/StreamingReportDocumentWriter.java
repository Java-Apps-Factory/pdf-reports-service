package com.example.reporting.pdf;

import com.example.reporting.pdf.a11y.TaggingContext;
import com.example.reporting.pdf.a11y.TaggingContext.ArtifactType;
import com.example.reporting.pdf.a11y.TaggingContext.Role;
import com.example.reporting.pdf.decorate.PageDecorator;
import com.example.reporting.pdf.decorate.SectionHeading;
import com.example.reporting.pdf.layout.Canvas;
import com.example.reporting.pdf.layout.KeepTogetherPolicy;
import com.example.reporting.pdf.layout.LayoutMetrics;
import com.example.reporting.pdf.layout.PageCursor;
import com.example.reporting.pdf.layout.PageGeometry;
import com.example.reporting.pdf.outline.BookmarkBuilder;
import com.example.reporting.pdf.table.CellValue;
import com.example.reporting.pdf.table.RowLayout;
import com.example.reporting.pdf.table.TableRenderer;
import com.example.reporting.pdf.text.FontFace;
import com.example.reporting.pdf.text.LoadedFonts;
import com.example.reporting.pdf.text.TextWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;

/** Pass 1 of the two-pass engine: lays out sections and rows page by page as they stream in. */
final class StreamingReportDocumentWriter implements ReportDocumentWriter {

    private static final System.Logger LOG = System.getLogger(StreamingReportDocumentWriter.class.getName());

    /** Smallest body height that must remain on page 1 below the reserved summary area. */
    static final float MIN_PAGE_ONE_BODY = 72f;

    private enum State { OPEN, IN_SECTION, FINISHED, CLOSED }

    private final DocumentSpec spec;
    private final PageGeometry geometry;
    private final PDDocument document;
    private final LoadedFonts fonts;
    private final TaggingContext tagging;
    private final TableRenderer tableRenderer;
    private final List<SectionHeading> headings = new ArrayList<>();
    private final List<Integer> sectionFirstPages = new ArrayList<>();

    private State state = State.OPEN;
    private PageCursor cursor;
    private PDPage page;
    private PDPageContentStream stream;
    private Canvas canvas;
    private boolean pageHasRows;
    private long rowsWritten;

    private SectionSpec section;
    private float[] columnWidths;
    private RowLayout headerRow;
    private boolean sectionStarted;
    private boolean tableOpen;
    private boolean sectionHasMessage;
    private long sectionRows;
    private int headingPage;
    private float headingTop;

    StreamingReportDocumentWriter(DocumentSpec spec, Path workDir, TaggingContext tagging) throws IOException {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.geometry = spec.geometry();
        this.tagging = Objects.requireNonNull(tagging, "tagging");
        this.tableRenderer = new TableRenderer(tagging);
        float summaryHeight = LayoutMetrics.summaryHeight(spec.summaryRowCount());
        if (geometry.bodyHeight(1) - summaryHeight < MIN_PAGE_ONE_BODY) {
            throw new IllegalArgumentException("Summary of " + spec.summaryRowCount()
                    + " rows leaves less than " + MIN_PAGE_ONE_BODY + " pt on page 1");
        }
        MemoryUsageSetting memory = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir.toFile());
        this.document = new PDDocument(memory.streamCache);
        try {
            this.fonts = LoadedFonts.load(document, spec.fonts());
            addPage();
            cursor = new PageCursor(1, geometry.bodyTop(1), geometry.bodyBottom());
            cursor.advance(summaryHeight);
        } catch (IOException | RuntimeException e) {
            document.close();
            throw e;
        }
    }

    @Override
    public void beginSection(SectionSpec newSection) throws IOException {
        requireState(State.OPEN);
        this.section = Objects.requireNonNull(newSection, "section");
        this.columnWidths = newSection.table().columnWidths(geometry.contentWidth());
        FontFace bold = fonts.bold();
        List<String> labels = new ArrayList<>();
        newSection.table().columns().forEach(column -> labels.add(bold.sanitize(column.label())));
        this.headerRow = RowLayout.header(newSection.table(), labels, columnWidths, bold.measurer());
        this.sectionStarted = false;
        this.tableOpen = false;
        this.sectionHasMessage = false;
        this.sectionRows = 0;
        this.state = State.IN_SECTION;
        tagging.beginStructure(Role.SECT);
    }

    @Override
    public void addRow(List<CellValue> cells) throws IOException {
        requireState(State.IN_SECTION);
        if (sectionHasMessage) {
            throw new IllegalStateException("Section already has the empty message");
        }
        FontFace regular = fonts.regular();
        List<CellValue> clean = new ArrayList<>(cells.size());
        for (CellValue cell : cells) {
            String text = regular.sanitize(cell.text());
            clean.add(cell.alignment().map(a -> CellValue.of(text, a)).orElseGet(() -> CellValue.of(text)));
        }
        RowLayout row = RowLayout.of(section.table(), clean, columnWidths, regular.measurer());
        if (!sectionStarted) {
            startSection(row.height());
        }
        if (sectionRows == 0) {
            tagging.beginStructure(Role.TBODY);
        }
        placeRow(row);
    }

    @Override
    public void addEmptyMessage(String message) throws IOException {
        requireState(State.IN_SECTION);
        if (sectionRows > 0 || sectionHasMessage) {
            throw new IllegalStateException("The empty message needs a section without rows or message");
        }
        if (!sectionStarted) {
            startSection(LayoutMetrics.emptyMessageHeight());
        }
        if (!cursor.fits(LayoutMetrics.emptyMessageHeight())) {
            continuePage();
        }
        closeTable();
        FontFace italic = fonts.italic();
        float size = LayoutMetrics.EMPTY_MESSAGE_SIZE;
        String text = TextWrapper.truncate(italic.sanitize(message), italic.measurer(), size,
                geometry.contentWidth());
        float lineTop = cursor.y() - LayoutMetrics.EMPTY_MESSAGE_SPACE_ABOVE;
        float x = geometry.contentLeft() + LayoutMetrics.CELL_PADDING_X;
        tagging.beginStructure(Role.P);
        tagging.markContent(stream, page, () -> canvas.text(italic.font(), size, x,
                LayoutMetrics.baseline(lineTop, LayoutMetrics.TABLE_LEADING, size), text));
        tagging.endStructure();
        cursor.advance(LayoutMetrics.emptyMessageHeight());
        sectionHasMessage = true;
    }

    @Override
    public void endSection() throws IOException {
        requireState(State.IN_SECTION);
        if (!sectionStarted) {
            startSection(0f);
        }
        closeTable();
        tagging.endStructure();
        headings.add(new SectionHeading(headingPage, headingTop, section.headingFor(sectionRows)));
        section = null;
        state = State.OPEN;
    }

    @Override
    public RenderResult finish(SummaryData summary, OutputStream out) throws IOException {
        requireState(State.OPEN);
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(out, "out");
        if (summary.rows().size() > spec.summaryRowCount()) {
            throw new IllegalArgumentException("Summary has " + summary.rows().size() + " rows but only "
                    + spec.summaryRowCount() + " were reserved");
        }
        stream.close();
        stream = null;
        new PageDecorator(geometry, fonts, tagging).decorate(document, spec, summary, headings);
        BookmarkBuilder bookmarks = new BookmarkBuilder().add(summary.heading(), 1, geometry.bodyTop(1));
        for (SectionHeading heading : headings) {
            bookmarks.add(heading.text(), heading.pageNumber(), heading.top());
        }
        bookmarks.build(document);
        applyMetadata();
        document.save(out);
        state = State.FINISHED;
        return RenderResult.builder()
                .pageCount(document.getNumberOfPages())
                .rowsWritten(rowsWritten)
                .sectionFirstPages(sectionFirstPages)
                .build();
    }

    @Override
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        try {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not close page content stream ({0})", spec.logContext());
        } finally {
            try {
                document.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not release PDF temp resources ({0})", spec.logContext());
            }
        }
    }

    private void startSection(float firstContentHeight) throws IOException {
        boolean atTop = cursor.isAtTop();
        if (KeepTogetherPolicy.startsOnNewPage(cursor, LayoutMetrics.headingBlockHeight(atTop),
                headerRow.height(), firstContentHeight)) {
            newPage();
            atTop = true;
        }
        if (!atTop) {
            cursor.advance(LayoutMetrics.HEADING_SPACE_ABOVE);
        }
        headingPage = cursor.pageNumber();
        headingTop = cursor.y();
        sectionFirstPages.add(headingPage);
        cursor.advance(LayoutMetrics.HEADING_LEADING + LayoutMetrics.HEADING_SPACE_BELOW);
        tagging.beginStructure(Role.TABLE);
        tableOpen = true;
        tagging.beginStructure(Role.THEAD);
        tableRenderer.drawRow(canvas, page, geometry.contentLeft(), cursor.y(), columnWidths, headerRow,
                fonts.bold(), LayoutMetrics.HEADER_FILL_GRAY, Role.TH, false);
        tagging.endStructure();
        cursor.advance(headerRow.height());
        pageHasRows = false;
        sectionStarted = true;
    }

    private void placeRow(RowLayout layout) throws IOException {
        RowLayout row = layout;
        if (!cursor.fits(row.height())) {
            if (pageHasRows || cursor.remaining() < LayoutMetrics.rowHeight(1)) {
                continuePage();
            }
            if (!cursor.fits(row.height())) {
                row = row.fitTo(cursor.remaining());
                LOG.log(Level.WARNING, "Row {0} of a section was cut to fit one page ({1})", sectionRows + 1,
                        spec.logContext());
            }
        }
        sectionRows++;
        rowsWritten++;
        float fill = sectionRows % 2 == 0 ? LayoutMetrics.EVEN_ROW_FILL_GRAY : TableRenderer.NO_FILL;
        tableRenderer.drawRow(canvas, page, geometry.contentLeft(), cursor.y(), columnWidths, row,
                fonts.regular(), fill, Role.TD, false);
        cursor.advance(row.height());
        pageHasRows = true;
    }

    /** Starts a continuation page: continuation label and repeated header row, both artifacts. */
    private void continuePage() throws IOException {
        newPage();
        FontFace italic = fonts.italic();
        float size = LayoutMetrics.CONTINUATION_SIZE;
        String label = TextWrapper.truncate(italic.sanitize(section.continuationLabel()), italic.measurer(), size,
                geometry.contentWidth());
        tagging.beginArtifact(stream, ArtifactType.PAGINATION);
        canvas.text(italic.font(), size, geometry.contentLeft(),
                LayoutMetrics.baseline(cursor.y(), LayoutMetrics.CONTINUATION_LEADING, size), label);
        tagging.endArtifact(stream);
        cursor.advance(LayoutMetrics.continuationHeight());
        tableRenderer.drawRow(canvas, page, geometry.contentLeft(), cursor.y(), columnWidths, headerRow,
                fonts.bold(), LayoutMetrics.HEADER_FILL_GRAY, Role.TH, true);
        cursor.advance(headerRow.height());
    }

    private void newPage() throws IOException {
        stream.close();
        addPage();
        int next = cursor.pageNumber() + 1;
        cursor.startPage(next, geometry.bodyTop(next), geometry.bodyBottom());
        pageHasRows = false;
    }

    private void addPage() throws IOException {
        page = new PDPage(new PDRectangle(geometry.width(), geometry.height()));
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        canvas = new Canvas(stream);
    }

    private void closeTable() {
        if (!tableOpen) {
            return;
        }
        if (sectionRows > 0) {
            tagging.endStructure();
        }
        tagging.endStructure();
        tableOpen = false;
    }

    private void applyMetadata() {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(spec.metadataTitle());
        info.setSubject(spec.subject());
        info.setAuthor(spec.author());
        info.setCreator("report-pdf-core");
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        catalog.setLanguage(spec.language());
        PDViewerPreferences preferences = new PDViewerPreferences(new COSDictionary());
        preferences.setDisplayDocTitle(true);
        catalog.setViewerPreferences(preferences);
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException("Writer is " + state + ", expected " + expected);
        }
    }
}
