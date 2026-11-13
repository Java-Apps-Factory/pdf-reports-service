package com.example.reporting.api;

import com.example.reporting.api.dto.DocumentResponse;
import com.example.reporting.orchestration.ReportQueryService;
import com.example.reporting.tracking.ReportDocument;
import com.example.reporting.tracking.ReportRun;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Report documents: detail and PDF download (SPEC section 14; retransmit arrives in Phase 4). */
@RestController
@RequestMapping("/api/v1/report-documents")
public class ReportDocumentController {

    private final ReportQueryService queries;
    private final ApiMapper mapper;

    /**
     * Creates the controller.
     *
     * @param queries read side
     * @param mapper response mapper
     */
    public ReportDocumentController(ReportQueryService queries, ApiMapper mapper) {
        this.queries = queries;
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

    private ReportDocument find(long docId) {
        return queries.document(docId).orElseThrow(() -> new ApiExceptions.NotFound("Document " + docId
                + " does not exist."));
    }
}
