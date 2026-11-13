package com.example.reporting.api;

/** The single error body of the API, modeled on RFC 7807 (SPEC section 14). */
public final class ApiProblem {

    private static final String TYPE_BASE = "https://example.com/problems/";

    private final String type;
    private final String title;
    private final int status;
    private final String detail;
    private final String instance;

    /**
     * Creates the problem.
     *
     * @param slug problem type slug, e.g. {@code run-already-active}
     * @param title short title
     * @param status HTTP status
     * @param detail explanation without record values
     * @param instance request path
     */
    public ApiProblem(String slug, String title, int status, String detail, String instance) {
        this.type = TYPE_BASE + slug;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }
}
