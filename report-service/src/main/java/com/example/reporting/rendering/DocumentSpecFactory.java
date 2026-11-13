package com.example.reporting.rendering;

import com.example.reporting.data.ReportCategory;
import com.example.reporting.pdf.DocumentSpec;
import com.example.reporting.pdf.SectionSpec;
import com.example.reporting.pdf.table.Alignment;
import com.example.reporting.pdf.table.ColumnSpec;
import com.example.reporting.pdf.table.TableSpec;
import com.example.reporting.pdf.text.FontSet;
import com.example.reporting.profile.ColumnDefinition;
import com.example.reporting.profile.ReportingProperties;
import com.example.reporting.profile.StateProfile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the report-pdf-core specs from a state profile and reference data: title block, running header,
 * footer, metadata and per-category tables (SPEC section 11). Font bytes are loaded once at startup.
 */
@Component
public final class DocumentSpecFactory {

    private static final String DASH = " – ";
    private static final DateTimeFormatter AS_OF =
            DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a v", Locale.US);

    private final FontSet fonts;
    private final ZoneId zone;
    private final boolean tagging;

    /**
     * Creates the factory and caches the font bytes.
     *
     * @param properties reporting configuration
     * @param resourceLoader loads font resources
     */
    public DocumentSpecFactory(ReportingProperties properties, ResourceLoader resourceLoader) {
        ReportingProperties.Fonts locations = properties.getFonts();
        this.fonts = FontSet.builder()
                .regular(read(resourceLoader, locations.getRegular()))
                .bold(read(resourceLoader, locations.getBold()))
                .italic(read(resourceLoader, locations.getItalic()))
                .build();
        this.zone = properties.getBusinessZone();
        this.tagging = properties.isTaggingEnabled();
    }

    /**
     * Builds the document spec of one report.
     *
     * @param request what is rendered
     * @param profile the state's profile
     * @param logo logo image bytes
     * @param locality locality name of the FIPS code
     * @param categoryCount number of active categories (summary rows)
     * @return document spec
     */
    public DocumentSpec documentSpec(ReportRequest request, StateProfile profile, byte[] logo, String locality,
            int categoryCount) {
        String fips = request.fipsCode();
        String dataAsOf = dataAsOfLine(request.dataAsOf());
        String headerLeft = profile.getTitle() + DASH + "FIPS " + fips + " " + locality;
        return DocumentSpec.builder()
                .fonts(fonts)
                .logo(logo, profile.getLogo().getAltText())
                .title(profile.getTitle())
                .subtitle(profile.getSubtitle())
                .fipsLine("FIPS " + fips + DASH + locality)
                .addInfoLine(dataAsOf)
                .addInfoLine("Report ID " + reportId(request.runId(), request.docId()))
                .runningHeader(headerLeft, dataAsOf)
                .footerDisclaimer(profile.getFooterDisclaimer())
                .reportId(reportId(request.runId(), request.docId()))
                .metadata(headerLeft, "FIPS " + fips + DASH + locality, profile.getSubtitle())
                .summaryRowCount(categoryCount)
                .taggingEnabled(tagging)
                .logContext("state=" + request.stateCode() + " fips=" + fips + " doc=" + request.docId())
                .build();
    }

    /**
     * Builds the section spec of one category.
     *
     * @param profile the state's profile
     * @param category the category
     * @return section spec with "(n records)" and "(1 record)" headings
     */
    public SectionSpec sectionSpec(StateProfile profile, ReportCategory category) {
        String label = categoryLabel(category);
        return SectionSpec.builder()
                .heading(label + " (" + SectionSpec.COUNT_TOKEN + " records)")
                .singularHeading(label + " (" + SectionSpec.COUNT_TOKEN + " record)")
                .continuationLabel(category.displayName() + " (continued)")
                .table(tableSpec(profile.columnsFor(category.code())))
                .build();
    }

    /**
     * Returns the label of a category in headings, bookmarks and the summary.
     *
     * @param category the category
     * @return "Category {order} – {display name}"
     */
    public static String categoryLabel(ReportCategory category) {
        return "Category " + category.displayOrder() + DASH + category.displayName();
    }

    /**
     * Returns the report ID printed on the report.
     *
     * @param runId run ID
     * @param docId document ID
     * @return "R-{runId}-{docId}"
     */
    public static String reportId(long runId, long docId) {
        return "R-" + runId + "-" + docId;
    }

    /**
     * Returns the "Data as of" line in the business time zone, e.g. "Data as of 11/10/2026 02:00 AM ET".
     *
     * @param dataAsOf run start time
     * @return the line
     */
    public String dataAsOfLine(Instant dataAsOf) {
        return "Data as of " + AS_OF.format(dataAsOf.atZone(zone));
    }

    static TableSpec tableSpec(List<ColumnDefinition> columns) {
        TableSpec.Builder table = TableSpec.builder();
        for (ColumnDefinition column : columns) {
            table.addColumn(ColumnSpec.builder(column.getLabel(), column.getWidth())
                    .alignment(column.getType().isRightAligned() ? Alignment.RIGHT : Alignment.LEFT)
                    .wrap(column.isWrap())
                    .build());
        }
        return table.build();
    }

    private static byte[] read(ResourceLoader loader, String location) {
        try (InputStream in = loader.getResource(location).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read font " + location, e);
        }
    }
}
