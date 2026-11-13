package com.example.reporting.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Presentation settings of one state (FR-08): texts, logo, columns, per-category column overrides and the
 * daily schedule. Bound from {@code reporting.states.STATE}; reference data lives in the database (AD-10).
 */
public final class StateProfile {

    private static final String DEFAULT_EMPTY_MESSAGE = "No content to display in this category.";

    private final boolean enabled;

    @NotBlank
    private final String cron;

    @NotBlank
    private final String title;

    @NotNull
    private final String subtitle;

    @NotNull
    private final String footerDisclaimer;

    @NotBlank
    private final String emptyCategoryMessage;

    @Valid
    @NotNull
    private final Logo logo;

    @Valid
    @NotEmpty
    private final List<ColumnDefinition> columns;

    @Valid
    @NotNull
    private final Map<String, List<ColumnDefinition>> categoryColumns;

    /**
     * Creates the profile; called by Spring Boot's binder.
     *
     * @param enabled whether the daily job runs for this state
     * @param cron daily schedule (Spring cron, business time zone)
     * @param title report title
     * @param subtitle subtitle, usually the state name
     * @param footerDisclaimer footer disclaimer
     * @param emptyCategoryMessage message under the header of an empty category
     * @param logo logo resource and alt text
     * @param columns default columns
     * @param categoryColumns optional column lists per category code
     */
    @ConstructorBinding
    public StateProfile(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 0 2 * * *") String cron,
            String title,
            @DefaultValue("") String subtitle,
            @DefaultValue("") String footerDisclaimer,
            @DefaultValue(DEFAULT_EMPTY_MESSAGE) String emptyCategoryMessage,
            Logo logo,
            List<ColumnDefinition> columns,
            Map<String, List<ColumnDefinition>> categoryColumns) {
        this.enabled = enabled;
        this.cron = cron;
        this.title = title;
        this.subtitle = subtitle;
        this.footerDisclaimer = footerDisclaimer;
        this.emptyCategoryMessage = emptyCategoryMessage;
        this.logo = logo;
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.categoryColumns = categoryColumns == null ? Map.of() : copy(categoryColumns);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCron() {
        return cron;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getFooterDisclaimer() {
        return footerDisclaimer;
    }

    public String getEmptyCategoryMessage() {
        return emptyCategoryMessage;
    }

    public Logo getLogo() {
        return logo;
    }

    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    public Map<String, List<ColumnDefinition>> getCategoryColumns() {
        return Collections.unmodifiableMap(categoryColumns);
    }

    /**
     * Returns the columns of one category: its override if configured, otherwise the default columns.
     *
     * @param categoryCode category code
     * @return unmodifiable column list
     */
    public List<ColumnDefinition> columnsFor(String categoryCode) {
        return categoryColumns.getOrDefault(categoryCode, columns);
    }

    private static Map<String, List<ColumnDefinition>> copy(Map<String, List<ColumnDefinition>> source) {
        Map<String, List<ColumnDefinition>> copy = new LinkedHashMap<>();
        source.forEach((code, list) -> copy.put(code, list == null ? List.of() : List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }

    /** Logo resource and its alternate text. */
    public static final class Logo {

        @NotBlank
        private final String resource;

        private final String altText;

        /**
         * Creates the logo settings.
         *
         * @param resource Spring resource location, e.g. {@code classpath:branding/logo.png}
         * @param altText alternate text; required, checked by {@link ProfileValidator}
         */
        @ConstructorBinding
        public Logo(String resource, @DefaultValue("") String altText) {
            this.resource = resource;
            this.altText = altText;
        }

        public String getResource() {
            return resource;
        }

        public String getAltText() {
            return altText;
        }
    }
}
