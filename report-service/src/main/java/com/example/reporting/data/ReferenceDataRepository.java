package com.example.reporting.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads reference data (states, FIPS codes, categories) with plain JDBC. */
@Repository
public class ReferenceDataRepository {

    private final JdbcTemplate jdbc;
    private final String categoriesSql;
    private final String allCategoriesSql;
    private final String localitySql;
    private final String statesSql;
    private final String fipsCodesSql;

    /**
     * Creates the repository.
     *
     * @param jdbc the application JdbcTemplate (runs after Flyway)
     */
    public ReferenceDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.categoriesSql = SqlResources.load("active-categories.sql");
        this.allCategoriesSql = SqlResources.load("all-active-categories.sql");
        this.localitySql = SqlResources.load("fips-locality.sql");
        this.statesSql = SqlResources.load("states.sql");
        this.fipsCodesSql = SqlResources.load("active-fips-codes.sql");
    }

    /**
     * Returns the active categories of a state in display order.
     *
     * @param stateCode state code
     * @return categories, possibly empty
     */
    public List<ReportCategory> activeCategories(String stateCode) {
        return jdbc.query(categoriesSql, (rs, n) -> new ReportCategory(rs.getString("CATEGORY_CODE").trim(),
                rs.getString("DISPLAY_NAME"), rs.getInt("DISPLAY_ORDER")), stateCode);
    }

    /**
     * Returns the active category codes of every state.
     *
     * @return state code to category codes
     */
    public Map<String, Set<String>> activeCategoryCodesByState() {
        Map<String, Set<String>> result = new HashMap<>();
        jdbc.query(allCategoriesSql, rs -> {
            result.computeIfAbsent(rs.getString("STATE_CODE").trim(), k -> new HashSet<>())
                    .add(rs.getString("CATEGORY_CODE").trim());
        });
        return result;
    }

    /**
     * Returns the locality name of an active FIPS code of the state.
     *
     * @param stateCode state code
     * @param fipsCode FIPS code
     * @return the name, or empty if the code is unknown or inactive
     */
    public Optional<String> localityName(String stateCode, String fipsCode) {
        return jdbc.query(localitySql, (rs, n) -> rs.getString("LOCALITY_NAME"), stateCode, fipsCode)
                .stream().findFirst();
    }

    /**
     * Returns the active FIPS codes of a state.
     *
     * @param stateCode state code
     * @return codes
     */
    public Set<String> activeFipsCodes(String stateCode) {
        return new HashSet<>(jdbc.query(fipsCodesSql, (rs, n) -> rs.getString(1).trim(), stateCode));
    }

    /**
     * Returns every state row with its active flag.
     *
     * @return state code to active
     */
    public Map<String, Boolean> states() {
        Map<String, Boolean> result = new HashMap<>();
        jdbc.query(statesSql, rs -> {
            result.put(rs.getString("STATE_CODE").trim(), rs.getInt("ACTIVE") == 1);
        });
        return result;
    }
}
