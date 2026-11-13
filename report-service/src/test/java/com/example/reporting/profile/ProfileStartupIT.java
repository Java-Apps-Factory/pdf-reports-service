package com.example.reporting.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.reporting.ReportServiceApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Bad profile configuration stops startup (SPEC section 21, Phase 2); a valid one binds as configured. Each
 * case also activates a profile named like its database, so {@code application-badwidth.yml} and
 * {@code application-badkey.yml} can replace the whole column list (Boot never merges lists across sources).
 */
class ProfileStartupIT {

    @Test
    void columnWidthsNotSummingTo100StopStartup() {
        assertThat(startupFailure("badwidth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("widths sum to 101");
    }

    @Test
    void unknownColumnKeyAndMissingLogoStopStartup() {
        assertThat(startupFailure("badkey"))
                .hasMessageContaining("key ssn is not produced by ReportRowMapper")
                .hasMessageContaining("does not exist");
    }

    @Test
    void shortGenerationLeaseStopsStartup() {
        assertThat(startupFailure("badlease", "reporting.generation.lease-duration=5m"))
                .hasMessageContaining("lease-duration must be longer than 5 minutes");
    }

    @Test
    void theShippedProfileBindsAsConfigured() {
        try (ConfigurableApplicationContext context = start("goodprofile")) {
            ProfileRegistry registry = context.getBean(ProfileRegistry.class);
            StateProfile va = registry.profile("VA").orElseThrow();
            assertThat(registry.stateCodes()).containsExactly("VA");
            assertThat(va.isEnabled()).isTrue();
            assertThat(va.getCron()).isEqualTo("0 0 2 * * *");
            assertThat(va.getColumns()).hasSize(7);
            assertThat(va.getColumns().get(4).getType()).isEqualTo(ColumnType.CURRENCY);
            assertThat(va.getColumns().get(1).isWrap()).isTrue();
            assertThat(va.getCategoryColumns()).isEmpty();
            assertThat(registry.logo("VA")).isNotEmpty();
            ReportingProperties properties = context.getBean(ReportingProperties.class);
            assertThat(properties.getGeneration().getFetchSize()).isEqualTo(500);
            assertThat(properties.getGeneration().getLeaseDuration()).hasMinutes(15);
            assertThat(properties.isTaggingEnabled()).as("PDF/UA tagging is on since Phase 5").isTrue();
            assertThat(properties.getColorProfile()).isEqualTo("classpath:color/sRGB.icc");
        }
    }

    private static Throwable startupFailure(String database, String... properties) {
        Throwable failure = catchThrowable(() -> start(database, properties).close());
        assertThat(failure).as("startup must fail").isNotNull();
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    private static ConfigurableApplicationContext start(String database, String... properties) {
        List<String> args = new ArrayList<>();
        args.add("--spring.datasource.url=jdbc:h2:mem:" + database
                + ";MODE=DB2;DATABASE_TO_UPPER=TRUE;DB_CLOSE_DELAY=-1");
        // Own broker id above 1000, so it never clashes with the ids Boot gives the cached test contexts' brokers.
        args.add("--spring.artemis.embedded.server-id=" + (Math.abs(database.hashCode()) % 1000 + 2000));
        args.add("--reporting.dev.seed-records=0");
        for (String property : properties) {
            // Command-line arguments override application.yml; builder default properties would not.
            args.add("--" + property);
        }
        return new SpringApplicationBuilder(ReportServiceApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("h2", "test", database)
                .run(args.toArray(new String[0]));
    }
}
