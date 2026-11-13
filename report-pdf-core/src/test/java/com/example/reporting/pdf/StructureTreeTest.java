package com.example.reporting.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDParentTreeValue;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.Revisions;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The structure tree follows SPEC section 12, so screen readers get the report in a logical order. */
class StructureTreeTest {

    @TempDir
    Path workDir;

    @Test
    void readingOrderIsTitleBlockThenSummaryThenEachCategory() throws Exception {
        try (PDDocument pdf = Loader.loadPDF(AccessibilityComplianceTest.render("empty-category", workDir))) {
            PDStructureTreeRoot root = pdf.getDocumentCatalog().getStructureTreeRoot();
            List<PDStructureElement> top = elements(root.getKids());
            assertThat(types(top)).containsExactly("Document");
            List<PDStructureElement> document = elements(top.get(0).getKids());
            assertThat(types(document)).containsExactly("H1", "Figure", "P", "P", "P", "P", "Sect", "Sect", "Sect");
            assertThat(document.get(1).getAlternateDescription()).isEqualTo("Placeholder agency logo");

            List<PDStructureElement> summary = elements(document.get(6).getKids());
            assertThat(types(summary)).containsExactly("H2", "Table");
            List<PDStructureElement> summaryTable = elements(summary.get(1).getKids());
            assertThat(types(summaryTable)).containsExactly("THead", "TBody");
            List<PDStructureElement> headerCells = elements(elements(summaryTable.get(0).getKids()).get(0).getKids());
            assertThat(types(headerCells)).containsExactly("TH", "TH");
            assertThat(headerCells).allSatisfy(th -> assertThat(scope(th)).isEqualTo("Column"));
            List<PDStructureElement> bodyRows = elements(summaryTable.get(1).getKids());
            assertThat(bodyRows).hasSize(3);
            List<PDStructureElement> totalRow = elements(bodyRows.get(2).getKids());
            assertThat(types(totalRow)).containsExactly("TH", "TD");
            assertThat(scope(totalRow.get(0))).isEqualTo("Row");

            assertThat(types(elements(document.get(7).getKids()))).containsExactly("H2", "Table");
            assertThat(types(elements(document.get(8).getKids()))).containsExactly("H2", "Table", "P");
            // An empty category's table has only its header row: Table → TR → TH (no THead without a TBody).
            List<PDStructureElement> emptyTable = elements(elements(document.get(8).getKids()).get(1).getKids());
            assertThat(types(emptyTable)).containsExactly("TR");
            assertThat(types(elements(emptyTable.get(0).getKids()))).containsOnly("TH").hasSize(7);
            assertThat(allTypes(top.get(0))).doesNotContain("NonStruct");
        }
    }

    @Test
    void everyPageIsTaggedWithAParentTreeEntryAndStructureTabOrder() throws Exception {
        try (PDDocument pdf = Loader.loadPDF(AccessibilityComplianceTest.render("multi-page-category", workDir))) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            assertThat(pdf.getDocumentCatalog().getMarkInfo().isMarked()).isTrue();
            assertThat(pdf.getDocumentCatalog().getLanguage()).isEqualTo("en-US");
            assertThat(pdf.getDocumentCatalog().getMetadata()).isNotNull();
            assertThat(pdf.getDocumentCatalog().getOutputIntents()).hasSize(1);
            PDStructureTreeRoot root = pdf.getDocumentCatalog().getStructureTreeRoot();
            PDNumberTreeNode parentTree = root.getParentTree();
            for (PDPage page : pdf.getPages()) {
                assertThat(page.getCOSObject().getNameAsString(COSName.getPDFName("Tabs"))).isEqualTo("S");
                int key = page.getStructParents();
                PDParentTreeValue value = (PDParentTreeValue) parentTree.getValue(key);
                assertThat(value).as("parent tree entry of page %d", key).isNotNull();
                assertThat(((COSArray) value.getCOSObject()).size()).isPositive();
            }
            assertThat(root.getParentTreeNextKey()).isEqualTo(pdf.getNumberOfPages());

            List<PDStructureElement> fees = elements(elements(elements(root.getKids()).get(0).getKids()).get(7)
                    .getKids());
            List<PDStructureElement> table = elements(fees.get(1).getKids());
            assertThat(types(table)).containsExactly("THead", "TBody");
            List<PDStructureElement> rows = elements(table.get(1).getKids());
            assertThat(rows).hasSize(120).allSatisfy(row -> {
                assertThat(types(elements(row.getKids()))).containsOnly("TD").hasSize(7);
                assertThat(elements(row.getKids())).allSatisfy(td -> assertThat(td.getPage()).isNotNull());
            });
        }
    }

    private static List<PDStructureElement> elements(List<Object> kids) {
        List<PDStructureElement> elements = new ArrayList<>();
        for (Object kid : kids) {
            if (kid instanceof PDStructureElement) {
                elements.add((PDStructureElement) kid);
            }
        }
        return elements;
    }

    private static List<String> types(List<PDStructureElement> elements) {
        return elements.stream().map(PDStructureElement::getStructureType).collect(Collectors.toList());
    }

    private static List<String> allTypes(PDStructureElement element) {
        List<String> types = new ArrayList<>();
        types.add(element.getStructureType());
        for (PDStructureElement kid : elements(element.getKids())) {
            types.addAll(allTypes(kid));
        }
        return types;
    }

    private static String scope(PDStructureElement cell) {
        Revisions<PDAttributeObject> attributes = cell.getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.getObject(i) instanceof PDTableAttributeObject) {
                return ((PDTableAttributeObject) attributes.getObject(i)).getScope();
            }
        }
        return null;
    }
}
