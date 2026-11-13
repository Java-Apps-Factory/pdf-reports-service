package com.example.reporting.api;

import com.example.reporting.api.dto.DocumentResponse;
import com.example.reporting.orchestration.ReportQueryService;
import com.example.reporting.orchestration.RetransmitService;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportRun;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Report documents: detail, PDF download and manual retransmit (SPEC section 14). */
@RestController
@RequestMapping("/api/v1/report-documents")
public class ReportDocumentController {

    private final ReportQueryService queries;
    private final RetransmitService retransmits;
    private final ApiMapper mapper;

    /**
     * Creates the controller.
     *
     * @param queries read side
     * @param retransmits manual retransmission
     * @param mapper response mapper
     */
    public ReportDocumentController(ReportQueryService queries, RetransmitService retransmits, ApiMapper mapper) {
        this.queries = queries;
        this.retransmits = retransmits;
        this.mapper = mapper;
    }

    /**
     * Returns one document.
     *
     * @param docId document ID
     * @return the document
     */
    @GetMapping("/{docId}")
    public DocumentResponse detail(@PathVariable long docId) {
        ReportDocument document = find(docId);
        return mapper.document(document,
                queries.localityName(document.getStateCode(), document.getFipsCode()).orElse(null),
                queries.contentAvailable(document));
    }

    /**
     * Streams the stored PDF: 404 if there is none, 410 if it was purged.
     *
     * @param docId document ID
     * @param response the servlet response written to
     * @throws IOException if the client connection fails
     */
    @GetMapping("/{docId}/content")
    public void content(@PathVariable long docId, HttpServletResponse response) throws IOException {
        ReportDocument document = find(docId);
        if (document.getContentPurgedAt() != null) {
            throw new ApiExceptions.ContentPurged("The PDF of document " + docId + " was purged.");
        }
        if (!queries.contentAvailable(document)) {
            throw new ApiExceptions.NotFound("Document " + docId + " has no stored PDF.");
        }
        ReportRun run = queries.run(document.getRunId()).orElseThrow(() -> new ApiExceptions.NotFound(
                "Run of document " + docId + " does not exist."));
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(mapper.fileName(document, run.getDataAsOf())).build().toString());
        if (document.getSizeBytes() != null) {
            response.setContentLengthLong(document.getSizeBytes());
        }
        OutputStream out = response.getOutputStream();
        queries.copyContent(docId, out);
        out.flush();
    }

    /**
     * Re-queues one SEND_FAILED or SEND_FAILED_FINAL document (202): 404 if missing, 409 if superseded or in
     * another status, 410 if purged.
     *
     * @param docId document ID
     * @return the re-queued document
     */
    @PostMapping("/{docId}/retransmit")
    public ResponseEntity<DocumentResponse> retransmit(@PathVariable long docId) {
        retransmits.retransmitDocument(docId);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/report-documents/" + docId))
                .body(detail(docId));
    }

    private ReportDocument find(long docId) {
        return queries.document(docId).orElseThrow(() -> new ApiExceptions.NotFound("Document " + docId
                + " does not exist."));
    }
}
