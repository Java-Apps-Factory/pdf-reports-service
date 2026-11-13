package com.example.reporting.api;

import com.example.reporting.orchestration.InvalidRunRequestException;
import com.example.reporting.orchestration.RunAlreadyActiveException;
import com.example.reporting.orchestration.UnknownStateException;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns every API error into an {@link ApiProblem}; the only place that builds error bodies. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    /**
     * Maps an active run to 409.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(RunAlreadyActiveException.class)
    public ResponseEntity<ApiProblem> runActive(RunAlreadyActiveException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "run-already-active", "Run already active", e.getMessage(), request);
    }

    /**
     * Maps an unknown state to 404.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(UnknownStateException.class)
    public ResponseEntity<ApiProblem> unknownState(UnknownStateException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "unknown-state", "Unknown state", e.getMessage(), request);
    }

    /**
     * Maps a missing run or document to 404.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(ApiExceptions.NotFound.class)
    public ResponseEntity<ApiProblem> notFound(ApiExceptions.NotFound e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage(), request);
    }

    /**
     * Maps purged content to 410.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(ApiExceptions.ContentPurged.class)
    public ResponseEntity<ApiProblem> purged(ApiExceptions.ContentPurged e, HttpServletRequest request) {
        return problem(HttpStatus.GONE, "content-purged", "Content purged", e.getMessage(), request);
    }

    /**
     * Maps an invalid run request to 400.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(InvalidRunRequestException.class)
    public ResponseEntity<ApiProblem> invalidRun(InvalidRunRequestException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    /**
     * Maps body validation errors to 400.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiProblem> invalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return badRequest(detail, request);
    }

    /**
     * Maps parameter validation errors to 400.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiProblem> invalidParameter(ConstraintViolationException e, HttpServletRequest request) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> lastNode(v.getPropertyPath().toString()) + " " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return badRequest(detail, request);
    }

    /**
     * Maps unreadable bodies and malformed parameters to 400.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    public ResponseEntity<ApiProblem> malformed(Exception e, HttpServletRequest request) {
        String detail = e instanceof MethodArgumentTypeMismatchException
                ? "Invalid value for parameter " + ((MethodArgumentTypeMismatchException) e).getName()
                : e instanceof MissingServletRequestParameterException ? e.getMessage()
                : "Request body is missing or not valid JSON";
        return badRequest(detail, request);
    }

    /**
     * Maps unsupported methods to 405.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiProblem> method(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed", e.getMessage(),
                request);
    }

    /**
     * Maps unsupported content types to 415.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiProblem> mediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported media type",
                e.getMessage(), request);
    }

    /**
     * Maps anything else to 500 without internal details.
     *
     * @param e the exception
     * @param request the request
     * @return problem response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiProblem> unexpected(Exception e, HttpServletRequest request) {
        LOG.error("Unexpected API error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal error",
                "Unexpected error; see the server log.", request);
    }

    private static ResponseEntity<ApiProblem> badRequest(String detail, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", detail, request);
    }

    private static ResponseEntity<ApiProblem> problem(HttpStatus status, String slug, String title, String detail,
            HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(PROBLEM_JSON)
                .body(new ApiProblem(slug, title, status.value(), detail, request.getRequestURI()));
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
