package com.example.reporting.pdf.a11y;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.reporting.pdf.a11y.TaggingContext.ArtifactType;
import com.example.reporting.pdf.a11y.TaggingContext.Role;
import com.example.reporting.pdf.a11y.TaggingContext.Slot;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.junit.jupiter.api.Test;

/** Structure-tree bookkeeping of {@link PdfUaTaggingContext} and its guards against misuse. */
class PdfUaTaggingContextTest {

    @Test
    void slotsInsertPassTwoElementsAtTheReservedPosition() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PdfUaTaggingContext tagging = new PdfUaTaggingContext();
            tagging.beginStructure(Role.DOCUMENT);
            Slot front = tagging.reserveSlot();
            tagging.beginStructure(Role.SECT);
            tagging.endStructure();
            tagging.endStructure();

            tagging.resumeSlot(front);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                tagging.beginStructure(Role.H1);
                tagging.markContent(stream, page, () -> { });
                tagging.endStructure();
                tagging.beginArtifact(stream, ArtifactType.LAYOUT);
                tagging.endArtifact(stream);
            }
            tagging.endSlot();
            tagging.finish(document);

            PDStructureElement root = (PDStructureElement) document.getDocumentCatalog().getStructureTreeRoot()
                    .getKids().get(0);
            assertThat(types(root.getKids())).containsExactly("H1", "Sect");
            assertThat(page.getStructParents()).isZero();
        }
    }

    @Test
    void contentOnAnotherPageIsLinkedThroughAMarkedContentReference() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage();
            PDPage second = new PDPage();
            document.addPage(first);
            document.addPage(second);
            PdfUaTaggingContext tagging = new PdfUaTaggingContext();
            tagging.beginStructure(Role.P);
            try (PDPageContentStream a = new PDPageContentStream(document, first);
                    PDPageContentStream b = new PDPageContentStream(document, second)) {
                tagging.markContent(a, first, () -> { });
                tagging.markContent(a, first, () -> { });
                tagging.markContent(b, second, () -> { });
            }
            tagging.endStructure();
            tagging.finish(document);

            PDStructureElement paragraph = (PDStructureElement) document.getDocumentCatalog()
                    .getStructureTreeRoot().getKids().get(0);
            List<Object> kids = paragraph.getKids();
            assertThat(kids).hasSize(3);
            assertThat(kids.get(0)).isEqualTo(0);
            assertThat(kids.get(1)).isEqualTo(1);
            assertThat(kids.get(2)).isInstanceOf(PDMarkedContentReference.class);
            assertThat(second.getStructParents()).isEqualTo(1);
        }
    }

    @Test
    void rejectsUnbalancedOrForeignCalls() throws IOException {
        PdfUaTaggingContext tagging = new PdfUaTaggingContext();
        assertThatThrownBy(tagging::endStructure).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(tagging::endSlot).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tagging.resumeSlot(new NoOpTaggingContext().reserveSlot()))
                .isInstanceOf(IllegalArgumentException.class);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                assertThatThrownBy(() -> tagging.markContent(stream, page, () -> { }))
                        .isInstanceOf(IllegalStateException.class);
            }
            tagging.beginStructure(Role.P);
            assertThatThrownBy(() -> tagging.finish(document)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("still open");
        }
    }

    @Test
    void xmpDeclaresConformanceAndEscapesText() {
        String packet = XmpMetadataWriter.packet("Fees & <Permits>", "FIPS \"51059\"", "", "tool");
        assertThat(packet).contains("<pdfaid:part>2</pdfaid:part>", "<pdfaid:conformance>A</pdfaid:conformance>",
                "<pdfuaid:part>1</pdfuaid:part>", "Fees &amp; &lt;Permits&gt;", "FIPS &quot;51059&quot;",
                "<pdfaSchema:prefix>pdfuaid</pdfaSchema:prefix>")
                .doesNotContain("dc:creator");
        assertThat(XmpMetadataWriter.packet("T", " ", "Agency", "tool")).doesNotContain("dc:description")
                .contains("<rdf:li>Agency</rdf:li>");
    }

    @Test
    void noOpContextDrawsWithoutStructure() throws IOException {
        NoOpTaggingContext noOp = new NoOpTaggingContext();
        boolean[] drawn = {false};
        noOp.beginHeaderCell(TaggingContext.Scope.ROW);
        noOp.resumeSlot(noOp.reserveSlot());
        noOp.endSlot();
        noOp.markContent(null, null, () -> drawn[0] = true);
        noOp.finish(null);
        assertThat(drawn[0]).isTrue();
        assertThat(Role.TBODY.pdfName()).isEqualTo("TBody");
    }

    private static List<String> types(List<Object> kids) {
        return kids.stream().filter(k -> k instanceof PDStructureElement)
                .map(k -> ((PDStructureElement) k).getStructureType()).collect(Collectors.toList());
    }
}
