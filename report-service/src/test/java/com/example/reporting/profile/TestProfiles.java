package com.example.reporting.profile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds profiles and properties in code for unit tests, mirroring application.yml. */
public final class TestProfiles {

    /** Logo shipped with the service. */
    public static final String LOGO = "classpath:branding/placeholder-logo.png";

    private TestProfiles() {
    }

    /**
     * Creates a column.
     *
     * @param key row key
     * @param type column type
     * @param width percent
     * @param wrap wrap flag or {@code null}
     * @return column
     */
    public static ColumnDefinition column(String key, ColumnType type, int width, Boolean wrap) {
        return new ColumnDefinition(key, key.toUpperCase(Locale.ROOT), type, width, wrap);
    }

    /**
     * Returns the default Virginia columns of SPEC section 11.
     *
     * @return seven columns summing to 100
     */
    public static List<ColumnDefinition> virginiaColumns() {
        return List.of(
                new ColumnDefinition("caseNumber", "Case Number", ColumnType.TEXT, 14, false),
                new ColumnDefinition("lastName", "Last Name", ColumnType.TEXT, 14, true),
                new ColumnDefinition("firstName", "First Name", ColumnType.TEXT, 14, true),
                new ColumnDefinition("eventDate", "Event Date", ColumnType.DATE, 10, false),
                new ColumnDefinition("amount", "Amount", ColumnType.CURRENCY, 11, false),
                new ColumnDefinition("status", "Status", ColumnType.TEXT, 10, false),
                new ColumnDefinition("description", "Description", ColumnType.TEXT, 27, true));
    }

    /**
     * Creates a state profile.
     *
     * @param columns default columns
     * @param overrides category column overrides
     * @param logo logo resource
     * @param altText logo alt text
     * @return profile
     */
    public static StateProfile profile(List<ColumnDefinition> columns, Map<String, List<ColumnDefinition>> overrides,
            String logo, String altText) {
        return new StateProfile(true, "0 0 2 * * *", "Commonwealth of Virginia – Locality Report", "Virginia",
                "Official use only. Generated automatically; do not edit.",
                "No content to display in this category.", new StateProfile.Logo(logo, altText), columns, overrides);
    }

    /**
     * Returns the standard Virginia profile.
     *
     * @return profile
     */
    public static StateProfile virginia() {
        return profile(virginiaColumns(), Map.of(), LOGO, "Placeholder agency logo");
    }

    /**
     * Creates properties with defaults for everything but the states and lease.
     *
     * @param states state profiles
     * @param lease generation lease
     * @param tempDir generation temp directory
     * @return properties
     */
    public static ReportingProperties properties(Map<String, StateProfile> states, Duration lease, Path tempDir) {
        return new ReportingProperties("node-1", ZoneId.of("America/New_York"),
                new ReportingProperties.Db("SELECT CURRENT_TIMESTAMP"),
                new ReportingProperties.Generation(4, Duration.ofSeconds(5), lease, 3,
                        new ReportingProperties.BackoffSettings(Duration.ofMinutes(1), 2.0, Duration.ofMinutes(30)),
                        500, tempDir),
                new ReportingProperties.Transmission("REPORT.OUT.QUEUE"),
                new ReportingProperties.Fonts("classpath:fonts/NotoSans-Regular.ttf",
                        "classpath:fonts/NotoSans-Bold.ttf", "classpath:fonts/NotoSans-Italic.ttf"),
                false, states,
                new ReportingProperties.Jobs(true, Duration.ofSeconds(30), Duration.ofSeconds(60)),
                new ReportingProperties.Dev(0));
    }

    /**
     * Returns properties with the Virginia profile and a 15 minute lease.
     *
     * @param tempDir generation temp directory
     * @return properties
     */
    public static ReportingProperties virginiaProperties(Path tempDir) {
        return properties(Map.of("VA", virginia()), Duration.ofMinutes(15), tempDir);
    }
}
