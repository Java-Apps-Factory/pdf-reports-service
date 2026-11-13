package com.example.reporting.maintenance;

import com.example.reporting.profile.ReportingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Deletes files older than one hour from this node's generation temp directory once at startup (SPEC section
 * 15): leftovers of a crash, never files of work in progress.
 */
@Component
public class TempFileJanitor implements ApplicationRunner {

    static final Duration MAX_AGE = Duration.ofHours(1);

    private static final Logger LOG = LoggerFactory.getLogger(TempFileJanitor.class);

    private final Path tempDir;
    private final Clock clock;

    /**
     * Creates the janitor.
     *
     * @param properties reporting configuration
     * @param clock application clock
     */
    public TempFileJanitor(ReportingProperties properties, Clock clock) {
        this.tempDir = properties.getGeneration().getTempDir();
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        clean();
    }

    /**
     * Deletes old regular files in the temp directory.
     *
     * @return number of files deleted
     */
    public int clean() {
        if (!Files.isDirectory(tempDir)) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(MAX_AGE);
        int deleted = 0;
        try (Stream<Path> files = Files.list(tempDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
        } catch (IOException e) {
            LOG.warn("Temp directory clean-up stopped early: {}", e.toString());
        }
        if (deleted > 0) {
            LOG.info("Deleted {} stale temp files", deleted);
        }
        return deleted;
    }
}
