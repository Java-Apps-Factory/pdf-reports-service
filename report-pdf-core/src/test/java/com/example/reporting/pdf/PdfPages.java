package com.example.reporting.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;

/** Reads a rendered PDF back for assertions: text per page (top to bottom) and bookmarks. */
final class PdfPages implements AutoCloseable {

    /** One outline entry. */
    static final class Bookmark {
        final String title;
        final int pageNumber;
        final int top;

        Bookmark(String title, int pageNumber, int top) {
            this.title = title;
            this.pageNumber = pageNumber;
            this.top = top;
        }
    }

    private final PDDocument document;
    private final List<String> pages = new ArrayList<>();

    PdfPages(byte[] pdf) throws IOException {
        this.document = Loader.loadPDF(pdf);
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        for (int i = 1; i <= document.getNumberOfPages(); i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            pages.add(stripper.getText(document));
        }
    }

    PDDocument document() {
        return document;
    }

    int pageCount() {
        return pages.size();
    }

    /** Text of a 1-based page. */
    String text(int pageNumber) {
        return pages.get(pageNumber - 1);
    }

    /** Non-blank lines of a 1-based page, top to bottom. */
    List<String> lines(int pageNumber) {
        return Arrays.stream(text(pageNumber).split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    List<Bookmark> bookmarks() throws IOException {
        List<Bookmark> result = new ArrayList<>();
        for (PDOutlineItem item : document.getDocumentCatalog().getDocumentOutline().children()) {
            PDPageXYZDestination destination = (PDPageXYZDestination) item.getDestination();
            int page = document.getPages().indexOf(destination.getPage()) + 1;
            result.add(new Bookmark(item.getTitle(), page, destination.getTop()));
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        document.close();
    }
}
