package com.example.reporting.data;

import com.example.reporting.profile.ReportingProperties;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * {@link ReportDataProvider} over JDBC (AD-04): {@code FOR READ ONLY} queries with a fetch size of 500, rows
 * handed one by one to the consumer through a {@link RowCallbackHandler}, never collected in a list.
 */
@Component
public final class JdbcReportDataProvider implements ReportDataProvider {

    private final JdbcTemplate jdbc;
    private final String rowsSql;
    private final String workloadSql;
    private final ReportRowMapper mapper = new ReportRowMapper();

    /**
     * Creates the provider with its own streaming {@link JdbcTemplate}.
     *
     * @param dataSource the application data source
     * @param properties reporting configuration (fetch size)
     */
    public JdbcReportDataProvider(DataSource dataSource, ReportingProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(properties.getGeneration().getFetchSize());
        this.rowsSql = SqlResources.load("report-rows.sql");
        this.workloadSql = SqlResources.load("fips-workload.sql");
    }

    @Override
    public List<FipsWorkload> workload(String stateCode) {
        return jdbc.query(workloadSql, (rs, rowNum) -> FipsWorkload.builder()
                .fipsCode(rs.getString("FIPS_CODE").trim())
                .recordCount(rs.getLong("RECORD_COUNT"))
                .unknownFips(rs.getInt("UNKNOWN_FIPS") == 1)
                .build(), stateCode).stream()
                .sorted(Comparator.comparing(FipsWorkload::fipsCode))
                .collect(Collectors.toList());
    }

    @Override
    public long streamRows(String stateCode, String fipsCode, Consumer<ReportRow> consumer) {
        long[] count = {0};
        jdbc.query(rowsSql, (RowCallbackHandler) rs -> {
            consumer.accept(mapper.mapRow(rs, (int) count[0]));
            count[0]++;
        }, stateCode, fipsCode);
        return count[0];
    }
}
