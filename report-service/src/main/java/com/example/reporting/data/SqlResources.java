package com.example.reporting.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Loads the SQL statements kept in {@code src/main/resources/sql} (SPEC section 19). */
final class SqlResources {

    private SqlResources() {
    }

    static String load(String name) {
        try (InputStream in = new ClassPathResource("sql/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing SQL resource sql/" + name, e);
        }
    }
}
