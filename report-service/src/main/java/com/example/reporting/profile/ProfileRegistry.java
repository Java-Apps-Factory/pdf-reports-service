package com.example.reporting.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Immutable snapshot of the validated state profiles and their logo bytes, built once at startup (Registry).
 * Construction runs {@link ProfileValidator}, so the application never starts with an invalid profile.
 */
@Component
public final class ProfileRegistry {

    private final Map<String, StateProfile> profiles;
    private final Map<String, byte[]> logos;

    /**
     * Validates the configuration and loads every logo.
     *
     * @param properties bound configuration
     * @param validator startup validator
     * @param resourceLoader loads logo resources
     */
    public ProfileRegistry(ReportingProperties properties, ProfileValidator validator,
            ResourceLoader resourceLoader) {
        validator.validate(properties);
        this.profiles = properties.getStates();
        Map<String, byte[]> loaded = new TreeMap<>();
        profiles.forEach((code, profile) -> loaded.put(code, read(resourceLoader, profile.getLogo().getResource())));
        this.logos = Collections.unmodifiableMap(loaded);
    }

    /**
     * Returns the profile of a state.
     *
     * @param stateCode state code
     * @return the profile, or empty if the state has none
     */
    public Optional<StateProfile> profile(String stateCode) {
        return Optional.ofNullable(profiles.get(stateCode));
    }

    /**
     * Returns a copy of the state's logo image.
     *
     * @param stateCode state code with a profile
     * @return image bytes
     * @throws IllegalArgumentException if the state has no profile
     */
    public byte[] logo(String stateCode) {
        byte[] bytes = logos.get(stateCode);
        if (bytes == null) {
            throw new IllegalArgumentException("No profile for state " + stateCode);
        }
        return bytes.clone();
    }

    /**
     * Returns every configured state code.
     *
     * @return sorted, unmodifiable set
     */
    public Set<String> stateCodes() {
        return profiles.keySet();
    }

    private static byte[] read(ResourceLoader loader, String location) {
        try (InputStream in = loader.getResource(location).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + location, e);
        }
    }
}
