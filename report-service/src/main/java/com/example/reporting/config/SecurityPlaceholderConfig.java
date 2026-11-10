package com.example.reporting.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The single place to add Spring Security later (NFR-12, SPEC section 22).
 *
 * <p>Authentication and authorization are out of scope for now. Until they are added, the
 * {@code prod} profile logs a warning at startup so the gap is never silent.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityPlaceholderConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityPlaceholderConfig.class);

    /**
     * Warns at startup in production while the API is unsecured.
     *
     * @return a runner that logs the warning once
     */
    @Bean
    @Profile("prod")
    public ApplicationRunner securityAbsentWarning() {
        return args -> LOG.warn("SECURITY IS NOT CONFIGURED: the REST API accepts unauthenticated requests. "
                + "Restrict network access until Spring Security is added in SecurityPlaceholderConfig.");
    }
}
