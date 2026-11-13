package com.example.reporting.rendering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;

/** Reads a rendered PDF back: non-blank text lines per page (top to bottom) and bookmark titles. */
final class PdfText {

    private final List<List<String>> pages = new ArrayList<>();
    private final List<String> bookmarks = new ArrayList<>();

    PdfText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(Arrays.stream(stripper.getText(document).split("\\R")).map(String::trim)
                        .filter(line -> !line.isEmpty()).collect(Collectors.toList()));
            }
            for (PDOutlineItem item : document.getDocumentCatalog().getDocumentOutline().children()) {
                bookmarks.add(item.getTitle());
            }
        }
    }

    int pageCount() {
        return pages.size();
    }

    List<String> page(int number) {
        return pages.get(number - 1);
    }

    List<String> allLines() {
        return pages.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    List<String> bookmarks() {
        return bookmarks;
    }

    /** Lines between the "Summary" heading and the total row on page 1. */
    List<String> summaryLines() {
        List<String> first = page(1);
        int start = first.indexOf("Summary");
        int end = start;
        while (!first.get(end).startsWith("Total ")) {
            end++;
        }
        return first.subList(start + 2, end + 1);
    }
}
