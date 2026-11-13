package com.example.reporting.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.reporting.TestRecords;
import com.example.reporting.TrackingRows;
import com.example.reporting.orchestration.ReportRunService;
import com.example.reporting.orchestration.StartRunCommand;
import com.example.reporting.tracking.ReportRun;
import com.example.reporting.tracking.TriggerType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * NFR-03 performance check (SPEC section 20, profile {@code perf}, {@code -Xmx2g}): a 100,000-record run, once in
 * one FIPS code and once spread over all 133 Virginia FIPS codes, from start to COMPLETED (generated, stored and
 * sent to the embedded broker). Records duration, peak heap and peak temp disk in
 * {@code target/perf-results.md}; the target is 10 minutes.
 */
@Tag("perf")
@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = {
    "spring.datasource.url=jdbc:h2:mem:perf;MODE=DB2;DATABASE_TO_UPPER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "reporting.dev.seed-records=0",
    "reporting.generation.temp-dir=target/perf-tmp"
})
@ActiveProfiles({"h2", "test"})
@TestConstructor(autowireMode = AutowireMode.ALL)
class PerformanceIT {

    private static final int RECORDS = 100_000;
    private static final Duration TARGET = Duration.ofMinutes(10);
    private static final Path RESULTS = Paths.get("target", "perf-results.md");
    private static final Path TEMP = Paths.get("target", "perf-tmp");

    private final JdbcTemplate jdbc;
    private final ReportRunService runService;

    PerformanceIT(JdbcTemplate jdbc, ReportRunService runService) {
        this.jdbc = jdbc;
        this.runService = runService;
    }

    @Test
    void oneFipsCodeWith100000Records() throws Exception {
        measure("100,000 records in one FIPS code (51059)", List.of("51059"));
    }

    @Test
    void recordsSpreadOverAll133FipsCodes() throws Exception {
        measure("100,000 records over 133 FIPS codes",
                jdbc.queryForList("SELECT FIPS_CODE FROM RPT_FIPS WHERE STATE_CODE = 'VA' ORDER BY FIPS_CODE",
                        String.class));
    }

    private void measure(String scenario, List<String> fipsCodes) throws Exception {
        TrackingRows rows = new TrackingRows(jdbc);
        rows.awaitVirginiaLockFree();
        jdbc.update("DELETE FROM RPT_RECORD");
        TestRecords.insert(jdbc, fipsCodes, RECORDS);
        System.gc();

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong peakHeap = new AtomicLong();
        AtomicLong peakTemp = new AtomicLong();
        Thread sampler = new Thread(() -> {
            Runtime runtime = Runtime.getRuntime();
            while (running.get()) {
                peakHeap.accumulateAndGet(runtime.totalMemory() - runtime.freeMemory(), Math::max);
                peakTemp.accumulateAndGet(directorySize(TEMP), Math::max);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "perf-sampler");
        sampler.setDaemon(true);
        sampler.start();

        long started = System.nanoTime();
        ReportRun run = runService.startRun(StartRunCommand.builder().stateCode("VA").triggerType(TriggerType.API)
                .requestedBy("perf").build());
        await().atMost(Duration.ofMinutes(30)).pollInterval(Duration.ofSeconds(1))
                .until(() -> "COMPLETED".equals(rows.runStatus(run.getRunId())));
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        running.set(false);
        sampler.join();

        long pages = jdbc.queryForObject("SELECT SUM(PAGE_COUNT) FROM REPORT_DOCUMENT WHERE RUN_ID = ?", Long.class,
                run.getRunId());
        long bytes = jdbc.queryForObject("SELECT SUM(SIZE_BYTES) FROM REPORT_DOCUMENT WHERE RUN_ID = ?", Long.class,
                run.getRunId());
        String line = String.format(Locale.US, "| %s | %s | %d docs | %,d pages | %.1f MB PDF | %d s | %d MB of %d MB "
                + "| %.1f MB |%n", LocalDateTime.now().withNano(0), scenario, run.getTotalDocuments(), pages,
                bytes / 1e6, took.toSeconds(), peakHeap.get() >> 20, Runtime.getRuntime().maxMemory() >> 20,
                peakTemp.get() / 1e6);
        if (!Files.exists(RESULTS)) {
            Files.writeString(RESULTS, "| When | Scenario | Documents | Pages | Total PDF | Duration | Peak heap "
                    + "| Peak temp disk |\n|---|---|---|---|---|---|---|---|\n", StandardCharsets.UTF_8);
        }
        Files.writeString(RESULTS, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        System.out.print("PERF " + line);
        assertThat(took).as("NFR-03 target for: " + scenario).isLessThanOrEqualTo(TARGET);
    }

    private static long directorySize(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.mapToLong(file -> file.toFile().length()).sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
