package com.example.reporting.pdf.a11y;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDParentTreeValue;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDTableAttributeObject;

/**
 * Builds the PDF/UA logical structure of SPEC section 12 by hand, since PDFBox has no high-level tagging API.
 *
 * <p>Every {@link #markContent} call wraps the drawing in a marked-content sequence with a fresh MCID for its
 * page, links the MCID to the open structure element (with a marked-content reference when the element lives on
 * another page) and records the element in that page's parent-tree entry. Artifacts are marked with their
 * subtype. Slots are placeholder elements that pass 2 inserts before and {@link #finish} removes. At the end the
 * structure tree root, parent tree, {@code /MarkInfo << /Marked true >>} and {@code /Tabs /S} on every page are
 * written. One instance per document; not thread-safe.
 */
public final class PdfUaTaggingContext implements TaggingContext {

    private static final String SLOT_TYPE = "NonStruct";
    private static final COSName TABS = COSName.getPDFName("Tabs");

    private final PDStructureTreeRoot root = new PDStructureTreeRoot();
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Map<COSDictionary, PageMarks> pages = new IdentityHashMap<>();
    private final Map<Integer, PageMarks> pagesByKey = new LinkedHashMap<>();
    private final List<SlotMarker> slots = new ArrayList<>();
    private int nextParentKey;

    /** Creates an empty structure tree. */
    public PdfUaTaggingContext() {
        stack.push(new Frame(root, null, null));
    }

    @Override
    public void beginStructure(Role role) {
        open(role);
    }

    @Override
    public void beginHeaderCell(Scope scope) {
        PDStructureElement cell = open(Role.TH);
        PDTableAttributeObject attributes = new PDTableAttributeObject();
        attributes.setScope(scope == Scope.ROW ? PDTableAttributeObject.SCOPE_ROW
                : PDTableAttributeObject.SCOPE_COLUMN);
        cell.addAttribute(attributes);
    }

    @Override
    public void beginFigure(String altText) {
        open(Role.FIGURE).setAlternateDescription(altText);
    }

    @Override
    public void endStructure() {
        Frame frame = stack.peek();
        if (frame == null || frame.element == null) {
            throw new IllegalStateException("No structure element is open");
        }
        stack.pop();
    }

    @Override
    public Slot reserveSlot() {
        Frame frame = stack.peek();
        PDStructureElement marker = new PDStructureElement(SLOT_TYPE, frame.node);
        attach(frame, marker);
        SlotMarker slot = new SlotMarker(frame.node, marker);
        slots.add(slot);
        return slot;
    }

    @Override
    public void resumeSlot(Slot slot) {
        if (!(slot instanceof SlotMarker) || !slots.contains(slot)) {
            throw new IllegalArgumentException("Slot of another context");
        }
        SlotMarker marker = (SlotMarker) slot;
        stack.push(new Frame(marker.parent, null, marker.marker));
    }

    @Override
    public void endSlot() {
        Frame frame = stack.peek();
        if (frame == null || frame.before == null) {
            throw new IllegalStateException("No slot is resumed");
        }
        stack.pop();
    }

    @Override
    public void markContent(PDPageContentStream stream, PDPage page, Drawing drawing) throws IOException {
        Frame frame = stack.peek();
        if (frame == null || frame.element == null) {
            throw new IllegalStateException("Marked content needs an open structure element");
        }
        PageMarks marks = marks(page);
        int mcid = marks.add(frame.element);
        COSDictionary properties = new COSDictionary();
        properties.setInt(COSName.MCID, mcid);
        stream.beginMarkedContent(COSName.getPDFName(frame.element.getStructureType()),
                PDPropertyList.create(properties));
        try {
            drawing.draw();
        } finally {
            stream.endMarkedContent();
        }
        link(frame.element, page, mcid);
    }

    @Override
    public void beginArtifact(PDPageContentStream stream, ArtifactType type) throws IOException {
        COSDictionary properties = new COSDictionary();
        properties.setName(COSName.TYPE, type == ArtifactType.PAGINATION ? "Pagination" : "Layout");
        stream.beginMarkedContent(COSName.ARTIFACT, PDPropertyList.create(properties));
    }

    @Override
    public void endArtifact(PDPageContentStream stream) throws IOException {
        stream.endMarkedContent();
    }

    @Override
    public void finish(PDDocument document) {
        if (stack.size() != 1) {
            throw new IllegalStateException(stack.size() - 1 + " structure elements are still open");
        }
        for (SlotMarker slot : slots) {
            slot.parent.removeKid(slot.marker);
        }
        Map<Integer, PDParentTreeValue> numbers = new LinkedHashMap<>();
        for (Map.Entry<Integer, PageMarks> entry : pagesByKey.entrySet()) {
            COSArray elements = new COSArray();
            for (PDStructureElement element : entry.getValue().elements) {
                elements.add(element.getCOSObject());
            }
            numbers.put(entry.getKey(), new PDParentTreeValue(elements));
        }
        PDNumberTreeNode parentTree = new PDNumberTreeNode(PDParentTreeValue.class);
        parentTree.setNumbers(numbers);
        root.setParentTree(parentTree);
        root.setParentTreeNextKey(nextParentKey);

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        catalog.setStructureTreeRoot(root);
        PDMarkInfo markInfo = new PDMarkInfo();
        markInfo.setMarked(true);
        catalog.setMarkInfo(markInfo);
        for (PDPage page : document.getPages()) {
            page.getCOSObject().setName(TABS, "S");
        }
    }

    private PDStructureElement open(Role role) {
        Frame parent = stack.peek();
        PDStructureElement element = new PDStructureElement(role.pdfName(), parent.node);
        attach(parent, element);
        stack.push(new Frame(element, element, null));
        return element;
    }

    private static void attach(Frame parent, PDStructureElement element) {
        if (parent.before != null) {
            parent.node.insertBefore(element, parent.before);
        } else {
            parent.node.appendKid(element);
        }
    }

    private PageMarks marks(PDPage page) {
        return pages.computeIfAbsent(page.getCOSObject(), dictionary -> {
            int key = nextParentKey++;
            page.setStructParents(key);
            PageMarks marks = new PageMarks();
            pagesByKey.put(key, marks);
            return marks;
        });
    }

    private static void link(PDStructureElement element, PDPage page, int mcid) {
        PDPage elementPage = element.getPage();
        if (elementPage == null) {
            element.setPage(page);
            element.appendKid(mcid);
        } else if (elementPage.getCOSObject() == page.getCOSObject()) {
            element.appendKid(mcid);
        } else {
            PDMarkedContentReference reference = new PDMarkedContentReference();
            reference.setPage(page);
            reference.setMCID(mcid);
            element.appendKid(reference);
        }
    }

    /** One level of the open-element stack; {@code before} is set while a slot is resumed. */
    private static final class Frame {
        private final PDStructureNode node;
        private final PDStructureElement element;
        private final PDStructureElement before;

        private Frame(PDStructureNode node, PDStructureElement element, PDStructureElement before) {
            this.node = node;
            this.element = element;
            this.before = before;
        }
    }

    /** Structure elements of one page, indexed by MCID. */
    private static final class PageMarks {
        private final List<PDStructureElement> elements = new ArrayList<>();

        private int add(PDStructureElement element) {
            elements.add(element);
            return elements.size() - 1;
        }
    }

    /** A placeholder element; pass 2 inserts before it and {@link #finish} removes it. */
    private static final class SlotMarker implements Slot {
        private final PDStructureNode parent;
        private final PDStructureElement marker;

        private SlotMarker(PDStructureNode parent, PDStructureElement marker) {
            this.parent = parent;
            this.marker = marker;
        }
    }
}
