package com.example.reporting.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.reporting.profile.TestProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TempFileJanitorTest {

    private static final Instant NOW = Instant.parse("2026-11-10T07:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void deletesFilesOlderThanOneHourOnly() throws Exception {
        Path old = Files.writeString(tempDir.resolve("doc-1.pdf"), "x");
        Files.setLastModifiedTime(old, FileTime.from(NOW.minus(Duration.ofMinutes(61))));
        Path fresh = Files.writeString(tempDir.resolve("doc-2.pdf"), "x");
        Files.setLastModifiedTime(fresh, FileTime.from(NOW.minus(Duration.ofMinutes(59))));
        Files.createDirectory(tempDir.resolve("sub"));

        TempFileJanitor janitor = new TempFileJanitor(TestProfiles.virginiaProperties(tempDir),
                Clock.fixed(NOW, ZoneOffset.UTC));
        janitor.run(null);

        assertThat(old).doesNotExist();
        assertThat(fresh).exists();
        assertThat(tempDir.resolve("sub")).exists();
    }

    @Test
    void aMissingDirectoryIsNotAnError() {
        TempFileJanitor janitor = new TempFileJanitor(TestProfiles.virginiaProperties(tempDir.resolve("none")),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(janitor.clean()).isZero();
    }
}
