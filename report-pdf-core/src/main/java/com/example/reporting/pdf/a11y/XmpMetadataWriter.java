package com.example.reporting.pdf.a11y;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;

/**
 * Writes the XMP metadata packet required by PDF/A-2a and PDF/UA-1 (SPEC section 12): {@code dc:title}
 * (matching the Info title), {@code dc:description} and {@code dc:creator} when present, {@code xmp:CreatorTool},
 * {@code pdfaid:part=2}, {@code pdfaid:conformance=A}, {@code pdfuaid:part=1}, and the PDF/A extension schema
 * that declares the {@code pdfuaid} namespace.
 */
public final class XmpMetadataWriter {

    private XmpMetadataWriter() {
    }

    /**
     * Builds the packet and stores it, uncompressed, as the document's metadata stream.
     *
     * @param document the document
     * @param title document title, same as the Info dictionary
     * @param subject subject, or blank to omit
     * @param author author, or blank to omit
     * @param creatorTool producing tool, same as the Info Creator
     * @throws IOException if the stream cannot be written
     */
    public static void write(PDDocument document, String title, String subject, String author, String creatorTool)
            throws IOException {
        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(packet(title, subject, author, creatorTool).getBytes(StandardCharsets.UTF_8));
        document.getDocumentCatalog().setMetadata(metadata);
    }

    /**
     * Returns the XMP packet text.
     *
     * @param title document title
     * @param subject subject, or blank to omit
     * @param author author, or blank to omit
     * @param creatorTool producing tool
     * @return the packet
     */
    public static String packet(String title, String subject, String author, String creatorTool) {
        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
                .append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
                .append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
                .append("  <rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                .append("   <dc:format>application/pdf</dc:format>\n")
                .append("   <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(escape(title))
                .append("</rdf:li></rdf:Alt></dc:title>\n");
        if (!isBlank(subject)) {
            xml.append("   <dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(escape(subject))
                    .append("</rdf:li></rdf:Alt></dc:description>\n");
        }
        if (!isBlank(author)) {
            xml.append("   <dc:creator><rdf:Seq><rdf:li>").append(escape(author))
                    .append("</rdf:li></rdf:Seq></dc:creator>\n");
        }
        xml.append("  </rdf:Description>\n")
                .append("  <rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">\n")
                .append("   <xmp:CreatorTool>").append(escape(creatorTool)).append("</xmp:CreatorTool>\n")
                .append("  </rdf:Description>\n")
                .append("  <rdf:Description rdf:about=\"\" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\">\n")
                .append("   <pdfaid:part>2</pdfaid:part>\n")
                .append("   <pdfaid:conformance>A</pdfaid:conformance>\n")
                .append("  </rdf:Description>\n")
                .append("  <rdf:Description rdf:about=\"\" xmlns:pdfuaid=\"http://www.aiim.org/pdfua/ns/id/\">\n")
                .append("   <pdfuaid:part>1</pdfuaid:part>\n")
                .append("  </rdf:Description>\n")
                .append("  <rdf:Description rdf:about=\"\"\n")
                .append("    xmlns:pdfaExtension=\"http://www.aiim.org/pdfa/ns/extension/\"\n")
                .append("    xmlns:pdfaSchema=\"http://www.aiim.org/pdfa/ns/schema#\"\n")
                .append("    xmlns:pdfaProperty=\"http://www.aiim.org/pdfa/ns/property#\">\n")
                .append("   <pdfaExtension:schemas><rdf:Bag><rdf:li rdf:parseType=\"Resource\">\n")
                .append("    <pdfaSchema:schema>PDF/UA Universal Accessibility Schema</pdfaSchema:schema>\n")
                .append("    <pdfaSchema:namespaceURI>http://www.aiim.org/pdfua/ns/id/</pdfaSchema:namespaceURI>\n")
                .append("    <pdfaSchema:prefix>pdfuaid</pdfaSchema:prefix>\n")
                .append("    <pdfaSchema:property><rdf:Seq><rdf:li rdf:parseType=\"Resource\">\n")
                .append("     <pdfaProperty:name>part</pdfaProperty:name>\n")
                .append("     <pdfaProperty:valueType>Integer</pdfaProperty:valueType>\n")
                .append("     <pdfaProperty:category>internal</pdfaProperty:category>\n")
                .append("     <pdfaProperty:description>Indicates, which part of ISO 14289 standard is followed")
                .append("</pdfaProperty:description>\n")
                .append("    </rdf:li></rdf:Seq></pdfaSchema:property>\n")
                .append("   </rdf:li></rdf:Bag></pdfaExtension:schemas>\n")
                .append("  </rdf:Description>\n")
                .append(" </rdf:RDF>\n")
                .append("</x:xmpmeta>\n")
                .append("<?xpacket end=\"w\"?>");
        return xml.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
