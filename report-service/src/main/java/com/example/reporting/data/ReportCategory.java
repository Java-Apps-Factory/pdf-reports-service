package com.example.reporting.data;

import java.util.Objects;

/** An active category of a state, from {@code RPT_CATEGORY}. */
public final class ReportCategory {

    private final String code;
    private final String displayName;
    private final int displayOrder;

    /**
     * Creates the category.
     *
     * @param code category code
     * @param displayName display name
     * @param displayOrder display order
     */
    public ReportCategory(String code, String displayName, int displayOrder) {
        this.code = Objects.requireNonNull(code, "code");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.displayOrder = displayOrder;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public int displayOrder() {
        return displayOrder;
    }
}
