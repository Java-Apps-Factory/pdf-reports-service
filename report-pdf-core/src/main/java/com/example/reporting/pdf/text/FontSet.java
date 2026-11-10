package com.example.reporting.pdf.text;

import java.util.Arrays;
import java.util.Objects;

/**
 * The three TrueType font programs a report uses: regular, bold and italic.
 *
 * <p>Holds raw font bytes only. Each document loads its own PDFBox font objects from these bytes, so one
 * {@code FontSet} can be shared by all writers and threads while PDFBox fonts never are (SPEC section 10).
 */
public final class FontSet {

    private final byte[] regular;
    private final byte[] bold;
    private final byte[] italic;

    private FontSet(Builder builder) {
        this.regular = copyNonEmpty(builder.regular, "regular");
        this.bold = copyNonEmpty(builder.bold, "bold");
        this.italic = copyNonEmpty(builder.italic, "italic");
    }

    /**
     * Returns a new builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a copy of the regular font program.
     *
     * @return TrueType bytes
     */
    public byte[] regular() {
        return regular.clone();
    }

    /**
     * Returns a copy of the bold font program.
     *
     * @return TrueType bytes
     */
    public byte[] bold() {
        return bold.clone();
    }

    /**
     * Returns a copy of the italic font program.
     *
     * @return TrueType bytes
     */
    public byte[] italic() {
        return italic.clone();
    }

    private static byte[] copyNonEmpty(byte[] bytes, String name) {
        Objects.requireNonNull(bytes, name + " font bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException(name + " font bytes are empty");
        }
        return Arrays.copyOf(bytes, bytes.length);
    }

    /** Builder for {@link FontSet}. All three fonts are required. */
    public static final class Builder {
        private byte[] regular;
        private byte[] bold;
        private byte[] italic;

        private Builder() {
        }

        /**
         * Sets the regular font.
         *
         * @param bytes TrueType bytes
         * @return this builder
         */
        public Builder regular(byte[] bytes) {
            this.regular = bytes;
            return this;
        }

        /**
         * Sets the bold font.
         *
         * @param bytes TrueType bytes
         * @return this builder
         */
        public Builder bold(byte[] bytes) {
            this.bold = bytes;
            return this;
        }

        /**
         * Sets the italic font.
         *
         * @param bytes TrueType bytes
         * @return this builder
         */
        public Builder italic(byte[] bytes) {
            this.italic = bytes;
            return this;
        }

        /**
         * Builds the font set.
         *
         * @return a new immutable font set
         */
        public FontSet build() {
            return new FontSet(this);
        }
    }
}
