package com.example.reporting.pdf.text;

/** Monospaced fake: every code point is half an em wide, so widths are easy to compute in tests. */
public final class FakeMeasurer implements TextMeasurer {

    @Override
    public float width(String text, float fontSize) {
        return text.codePointCount(0, text.length()) * fontSize * 0.5f;
    }
}
