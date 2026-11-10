package com.example.reporting.pdf;

import com.example.reporting.pdf.table.Alignment;
import com.example.reporting.pdf.table.CellValue;
import com.example.reporting.pdf.table.ColumnSpec;
import com.example.reporting.pdf.table.TableSpec;
import com.example.reporting.pdf.text.FontSet;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Shared test fixtures: bundled Noto Sans fonts, a generated logo, the Virginia column set and fake rows. */
public final class TestReports {

    /** Title used by test documents. */
    public static final String TITLE = "Cumulative Case Report";
    /** Report ID used by test documents. */
    public static final String REPORT_ID = "R-1702-48213";
    /** Running header left text. */
    public static final String RUNNING_LEFT = TITLE + " – FIPS 51059 Fairfax County";
    /** Running header right text. */
    public static final String RUNNING_RIGHT = "Data as of 11/10/2026 02:00 AM ET";

    private static final Path FONT_DIR = Paths.get("..", "report-service", "src", "main", "resources", "fonts");
    private static final String[] LAST_NAMES = {"Alder", "Birchwood", "Cedarly", "Dunmore", "Elmsworth",
        "Fernhill", "Glenrock", "Hawthorne", "Ivybridge", "Juniper"};
    private static final String[] FIRST_NAMES = {"Avery", "Blake", "Casey", "Devon", "Emery", "Finley",
        "Gray", "Harper", "Indy", "Jordan"};
    private static final FontSet FONTS = loadFonts();

    private TestReports() {
    }

    /**
     * Returns the bundled Noto Sans fonts (read from the report-service resources).
     *
     * @return font set
     */
    public static FontSet fonts() {
        return FONTS;
    }

    /**
     * Returns a small generated PNG logo.
     *
     * @return PNG bytes
     */
    public static byte[] logo() {
        BufferedImage image = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 120, 60);
        g.setColor(Color.DARK_GRAY);
        g.fillOval(10, 10, 40, 40);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns a document spec builder filled with test values.
     *
     * @param summaryRows summary rows to reserve
     * @return builder
     */
    public static DocumentSpec.Builder spec(int summaryRows) {
        return DocumentSpec.builder()
                .fonts(fonts())
                .logo(logo(), "Placeholder agency logo")
                .title(TITLE)
                .subtitle("Virginia")
                .fipsLine("FIPS 51059 – Fairfax County")
                .addInfoLine(RUNNING_RIGHT)
                .addInfoLine("Report ID " + REPORT_ID)
                .runningHeader(RUNNING_LEFT, RUNNING_RIGHT)
                .footerDisclaimer("Placeholder disclaimer: this report contains cumulative records for official "
                        + "use only. Do not redistribute without approval from the issuing agency.")
                .reportId(REPORT_ID)
                .metadata(TITLE + " FIPS 51059", "FIPS 51059", "Test Agency")
                .summaryRowCount(summaryRows)
                .logContext("51059");
    }

    /**
     * Returns the default Virginia table (SPEC section 11).
     *
     * @return table spec with seven columns
     */
    public static TableSpec virginiaTable() {
        return TableSpec.builder()
                .addColumn(ColumnSpec.builder("Case Number", 14).wrap(false).build())
                .addColumn(ColumnSpec.builder("Last Name", 14).build())
                .addColumn(ColumnSpec.builder("First Name", 14).build())
                .addColumn(ColumnSpec.builder("Event Date", 10).wrap(false).build())
                .addColumn(ColumnSpec.builder("Amount", 11).alignment(Alignment.RIGHT).wrap(false).build())
                .addColumn(ColumnSpec.builder("Status", 10).wrap(false).build())
                .addColumn(ColumnSpec.builder("Description", 27).build())
                .build();
    }

    /**
     * Returns a section spec over the Virginia table.
     *
     * @param number category number
     * @param name display name
     * @return section spec
     */
    public static SectionSpec section(int number, String name) {
        return SectionSpec.builder()
                .heading("Category " + number + " – " + name + " ({n} records)")
                .continuationLabel(name + " (continued)")
                .table(virginiaTable())
                .build();
    }

    /**
     * Returns a deterministic fake row for the Virginia table.
     *
     * @param i row index
     * @return seven cells
     */
    public static List<CellValue> row(int i) {
        String description = i % 7 == 0
                ? "Follow-up review scheduled after the initial assessment; additional documents were requested "
                        + "from the applicant and are pending verification by the county office."
                : "Routine entry " + i;
        return List.of(
                CellValue.of(String.format(Locale.US, "C-%07d", i)),
                CellValue.of(LAST_NAMES[i % LAST_NAMES.length]),
                CellValue.of(FIRST_NAMES[(i / 3) % FIRST_NAMES.length]),
                CellValue.of(String.format(Locale.US, "%02d/%02d/2026", 1 + i % 12, 1 + i % 28)),
                CellValue.of(String.format(Locale.US, "$%,d.%02d", i * 13, i % 100)),
                CellValue.of(i % 3 == 0 ? "Closed" : "Open"),
                CellValue.of(description));
    }

    private static FontSet loadFonts() {
        try {
            return FontSet.builder()
                    .regular(Files.readAllBytes(FONT_DIR.resolve("NotoSans-Regular.ttf")))
                    .bold(Files.readAllBytes(FONT_DIR.resolve("NotoSans-Bold.ttf")))
                    .italic(Files.readAllBytes(FONT_DIR.resolve("NotoSans-Italic.ttf")))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Fonts are read from " + FONT_DIR.toAbsolutePath(), e);
        }
    }
}
