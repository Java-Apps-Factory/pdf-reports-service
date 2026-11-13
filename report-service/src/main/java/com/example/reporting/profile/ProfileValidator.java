package com.example.reporting.profile;

import com.example.reporting.data.ReferenceDataRepository;
import com.example.reporting.data.ReportRowMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Startup cross-check of configuration against reference data (SPEC section 16, AD-10). Any problem stops
 * startup with one message listing all problems.
 *
 * <p>Fails when an enabled state has no row in {@code RPT_STATE}; an active state row has no profile; column
 * widths do not sum to 100; a column key is not produced by {@link ReportRowMapper} or its type does not fit
 * the column type; a logo resource is missing or has no alt text; a category override names a category that
 * is not active for the state; or {@code generation.lease-duration} is 5 minutes or less.
 */
@Component
public class ProfileValidator {

    /** Shortest allowed generation lease (exclusive). */
    static final Duration MIN_LEASE = Duration.ofMinutes(5);

    private static final int TOTAL_WIDTH = 100;

    private final ReferenceDataRepository referenceData;
    private final ResourceLoader resourceLoader;

    /**
     * Creates the validator.
     *
     * @param referenceData reference data reader
     * @param resourceLoader loads logo resources
     */
    public ProfileValidator(ReferenceDataRepository referenceData, ResourceLoader resourceLoader) {
        this.referenceData = referenceData;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Validates the configuration against the database.
     *
     * @param properties bound configuration
     * @throws IllegalStateException listing every problem found
     */
    public void validate(ReportingProperties properties) {
        List<String> problems = problems(properties, referenceData.states(),
                referenceData.activeCategoryCodesByState());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid reporting configuration:\n - " + String.join("\n - ", problems));
        }
    }

    /**
     * Returns every problem of the configuration; empty when it is valid.
     *
     * @param properties bound configuration
     * @param stateRows state codes in {@code RPT_STATE} with their active flag
     * @param activeCategories active category codes per state
     * @return problem descriptions
     */
    List<String> problems(ReportingProperties properties, Map<String, Boolean> stateRows,
            Map<String, Set<String>> activeCategories) {
        List<String> problems = new ArrayList<>();
        Duration lease = properties.getGeneration().getLeaseDuration();
        if (lease.compareTo(MIN_LEASE) <= 0) {
            problems.add("reporting.generation.lease-duration must be longer than 5 minutes, is " + lease);
        }
        stateRows.forEach((code, active) -> {
            if (active && !properties.getStates().containsKey(code)) {
                problems.add("Active state " + code + " in RPT_STATE has no profile under reporting.states");
            }
        });
        properties.getStates().forEach((code, profile) -> {
            String prefix = "reporting.states." + code;
            if (profile.isEnabled() && !stateRows.containsKey(code)) {
                problems.add(prefix + " is enabled but state " + code + " is not in RPT_STATE");
            }
            checkLogo(prefix, profile, problems);
            checkColumns(prefix + ".columns", profile.getColumns(), problems);
            Set<String> categories = activeCategories.getOrDefault(code, Set.of());
            profile.getCategoryColumns().forEach((category, columns) -> {
                String path = prefix + ".category-columns." + category;
                if (!categories.contains(category)) {
                    problems.add(path + " names a category that is not active for " + code);
                }
                checkColumns(path, columns, problems);
            });
        });
        return problems;
    }

    private void checkLogo(String prefix, StateProfile profile, List<String> problems) {
        StateProfile.Logo logo = profile.getLogo();
        if (logo.getAltText() == null || logo.getAltText().isBlank()) {
            problems.add(prefix + ".logo.alt-text is missing");
        }
        Resource resource = resourceLoader.getResource(logo.getResource());
        if (!resource.exists() || !resource.isReadable()) {
            problems.add(prefix + ".logo.resource " + logo.getResource() + " does not exist");
        }
    }

    private static void checkColumns(String path, List<ColumnDefinition> columns, List<String> problems) {
        if (columns.isEmpty()) {
            problems.add(path + " has no columns");
            return;
        }
        int sum = 0;
        for (ColumnDefinition column : columns) {
            sum += column.getWidth();
            Class<?> fieldType = ReportRowMapper.FIELD_TYPES.get(column.getKey());
            if (fieldType == null) {
                problems.add(path + ": key " + column.getKey() + " is not produced by ReportRowMapper "
                        + ReportRowMapper.keys());
            } else if (!column.getType().accepts(fieldType)) {
                problems.add(path + ": key " + column.getKey() + " holds " + fieldType.getSimpleName()
                        + " values and cannot be shown as " + column.getType());
            }
        }
        if (sum != TOTAL_WIDTH) {
            problems.add(path + ": widths sum to " + sum + ", must be 100");
        }
    }
}
