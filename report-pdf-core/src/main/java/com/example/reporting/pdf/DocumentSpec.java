package com.example.reporting.pdf;

import com.example.reporting.pdf.layout.PageGeometry;
import com.example.reporting.pdf.text.FontSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Document-level layout and content of one report: page geometry, fonts, logo, title block lines, running
 * header, footer, metadata, the number of summary rows to reserve on page 1, and whether tagging is on.
 *
 * <p>All texts are display-ready; the core does not format dates or numbers in them.
 */
public final class DocumentSpec {

    private final PageGeometry geometry;
    private final FontSet fonts;
    private final byte[] logo;
    private final String logoAltText;
    private final String title;
    private final String subtitle;
    private final String fipsLine;
    private final List<String> infoLines;
    private final String runningHeaderLeft;
    private final String runningHeaderRight;
    private final String footerDisclaimer;
    private final String reportId;
    private final String metadataTitle;
    private final String subject;
    private final String author;
    private final String language;
    private final int summaryRowCount;
    private final boolean taggingEnabled;
    private final String logContext;

    private DocumentSpec(Builder b) {
        this.geometry = Objects.requireNonNull(b.geometry, "geometry");
        this.fonts = Objects.requireNonNull(b.fonts, "fonts");
        Objects.requireNonNull(b.logo, "logo");
        if (b.logo.length == 0) {
            throw new IllegalArgumentException("logo is empty");
        }
        this.logo = Arrays.copyOf(b.logo, b.logo.length);
        this.logoAltText = requireText(b.logoAltText, "logoAltText");
        this.title = requireText(b.title, "title");
        this.subtitle = Objects.requireNonNull(b.subtitle, "subtitle");
        this.fipsLine = Objects.requireNonNull(b.fipsLine, "fipsLine");
        this.infoLines = List.copyOf(b.infoLines);
        this.runningHeaderLeft = Objects.requireNonNull(b.runningHeaderLeft, "runningHeaderLeft");
        this.runningHeaderRight = Objects.requireNonNull(b.runningHeaderRight, "runningHeaderRight");
        this.footerDisclaimer = Objects.requireNonNull(b.footerDisclaimer, "footerDisclaimer");
        this.reportId = requireText(b.reportId, "reportId");
        this.metadataTitle = b.metadataTitle == null ? title : b.metadataTitle;
        this.subject = Objects.requireNonNull(b.subject, "subject");
        this.author = Objects.requireNonNull(b.author, "author");
        this.language = requireText(b.language, "language");
        if (b.summaryRowCount < 0) {
            throw new IllegalArgumentException("summaryRowCount must be >= 0");
        }
        this.summaryRowCount = b.summaryRowCount;
        this.taggingEnabled = b.taggingEnabled;
        this.logContext = Objects.requireNonNull(b.logContext, "logContext");
    }

    /**
     * Returns a new builder. Fonts, logo, logo alt text, title and report ID are required.
     *
     * @return a builder with the SPEC section 11 defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the page geometry.
     *
     * @return geometry
     */
    public PageGeometry geometry() {
        return geometry;
    }

    /**
     * Returns the fonts.
     *
     * @return font set
     */
    public FontSet fonts() {
        return fonts;
    }

    /**
     * Returns a copy of the logo image bytes (PNG or JPEG).
     *
     * @return image bytes
     */
    public byte[] logo() {
        return logo.clone();
    }

    /**
     * Returns the logo's alternate text.
     *
     * @return alt text
     */
    public String logoAltText() {
        return logoAltText;
    }

    /**
     * Returns the report title (first title block line, 16 pt bold).
     *
     * @return title
     */
    public String title() {
        return title;
    }

    /**
     * Returns the subtitle (11 pt), for example the state name.
     *
     * @return subtitle, possibly empty
     */
    public String subtitle() {
        return subtitle;
    }

    /**
     * Returns the FIPS line (12 pt bold), for example "FIPS 51059 – Fairfax County".
     *
     * @return FIPS line, possibly empty
     */
    public String fipsLine() {
        return fipsLine;
    }

    /**
     * Returns the remaining title block lines (9 pt), for example the data-as-of and report ID lines.
     *
     * @return unmodifiable list
     */
    public List<String> infoLines() {
        return infoLines;
    }

    /**
     * Returns the left running header text on pages 2+.
     *
     * @return text
     */
    public String runningHeaderLeft() {
        return runningHeaderLeft;
    }

    /**
     * Returns the right running header text on pages 2+.
     *
     * @return text
     */
    public String runningHeaderRight() {
        return runningHeaderRight;
    }

    /**
     * Returns the footer disclaimer (at most two lines are shown).
     *
     * @return disclaimer, possibly empty
     */
    public String footerDisclaimer() {
        return footerDisclaimer;
    }

    /**
     * Returns the report ID shown in the footer centre.
     *
     * @return report ID
     */
    public String reportId() {
        return reportId;
    }

    /**
     * Returns the document title metadata; defaults to {@link #title()}.
     *
     * @return metadata title
     */
    public String metadataTitle() {
        return metadataTitle;
    }

    /**
     * Returns the subject metadata.
     *
     * @return subject, possibly empty
     */
    public String subject() {
        return subject;
    }

    /**
     * Returns the author metadata.
     *
     * @return author, possibly empty
     */
    public String author() {
        return author;
    }

    /**
     * Returns the document language.
     *
     * @return BCP 47 tag, default {@code en-US}
     */
    public String language() {
        return language;
    }

    /**
     * Returns the number of summary rows to reserve on page 1.
     *
     * @return row count, at least 0
     */
    public int summaryRowCount() {
        return summaryRowCount;
    }

    /**
     * Tells whether PDF/UA tagging is on.
     *
     * @return true for tagged output
     */
    public boolean taggingEnabled() {
        return taggingEnabled;
    }

    /**
     * Returns a non-sensitive identifier for log messages, such as the FIPS code. Never record content.
     *
     * @return log context, possibly empty
     */
    public String logContext() {
        return logContext;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /** Builder for {@link DocumentSpec}. */
    public static final class Builder {
        private PageGeometry geometry = PageGeometry.letterLandscape();
        private FontSet fonts;
        private byte[] logo;
        private String logoAltText;
        private String title;
        private String subtitle = "";
        private String fipsLine = "";
        private final List<String> infoLines = new ArrayList<>();
        private String runningHeaderLeft = "";
        private String runningHeaderRight = "";
        private String footerDisclaimer = "";
        private String reportId;
        private String metadataTitle;
        private String subject = "";
        private String author = "";
        private String language = "en-US";
        private int summaryRowCount;
        private boolean taggingEnabled;
        private String logContext = "";

        private Builder() {
        }

        /**
         * Sets the page geometry.
         *
         * @param value geometry
         * @return this builder
         */
        public Builder geometry(PageGeometry value) {
            this.geometry = value;
            return this;
        }

        /**
         * Sets the fonts.
         *
         * @param value font set
         * @return this builder
         */
        public Builder fonts(FontSet value) {
            this.fonts = value;
            return this;
        }

        /**
         * Sets the logo and its alternate text.
         *
         * @param imageBytes PNG or JPEG bytes
         * @param altText alternate text
         * @return this builder
         */
        public Builder logo(byte[] imageBytes, String altText) {
            this.logo = imageBytes == null ? null : imageBytes.clone();
            this.logoAltText = altText;
            return this;
        }

        /**
         * Sets the title.
         *
         * @param value title
         * @return this builder
         */
        public Builder title(String value) {
            this.title = value;
            return this;
        }

        /**
         * Sets the subtitle.
         *
         * @param value subtitle
         * @return this builder
         */
        public Builder subtitle(String value) {
            this.subtitle = value;
            return this;
        }

        /**
         * Sets the FIPS line.
         *
         * @param value FIPS line
         * @return this builder
         */
        public Builder fipsLine(String value) {
            this.fipsLine = value;
            return this;
        }

        /**
         * Appends a 9 pt title block line.
         *
         * @param value line text
         * @return this builder
         */
        public Builder addInfoLine(String value) {
            this.infoLines.add(Objects.requireNonNull(value, "infoLine"));
            return this;
        }

        /**
         * Sets the running header texts.
         *
         * @param left left text
         * @param right right text
         * @return this builder
         */
        public Builder runningHeader(String left, String right) {
            this.runningHeaderLeft = left;
            this.runningHeaderRight = right;
            return this;
        }

        /**
         * Sets the footer disclaimer.
         *
         * @param value disclaimer
         * @return this builder
         */
        public Builder footerDisclaimer(String value) {
            this.footerDisclaimer = value;
            return this;
        }

        /**
         * Sets the report ID.
         *
         * @param value report ID
         * @return this builder
         */
        public Builder reportId(String value) {
            this.reportId = value;
            return this;
        }

        /**
         * Sets the metadata title, subject and author.
         *
         * @param metaTitle document title
         * @param metaSubject subject
         * @param metaAuthor author
         * @return this builder
         */
        public Builder metadata(String metaTitle, String metaSubject, String metaAuthor) {
            this.metadataTitle = metaTitle;
            this.subject = metaSubject;
            this.author = metaAuthor;
            return this;
        }

        /**
         * Sets the document language.
         *
         * @param value BCP 47 tag
         * @return this builder
         */
        public Builder language(String value) {
            this.language = value;
            return this;
        }

        /**
         * Sets the number of summary rows to reserve on page 1.
         *
         * @param value row count
         * @return this builder
         */
        public Builder summaryRowCount(int value) {
            this.summaryRowCount = value;
            return this;
        }

        /**
         * Turns tagging on or off.
         *
         * @param value true for PDF/UA tagging
         * @return this builder
         */
        public Builder taggingEnabled(boolean value) {
            this.taggingEnabled = value;
            return this;
        }

        /**
         * Sets the log context.
         *
         * @param value non-sensitive identifier, such as the FIPS code
         * @return this builder
         */
        public Builder logContext(String value) {
            this.logContext = value;
            return this;
        }

        /**
         * Builds the spec.
         *
         * @return immutable spec
         */
        public DocumentSpec build() {
            return new DocumentSpec(this);
        }
    }
}
