package com.example.reporting.config;

import com.example.reporting.profile.ReportingProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application {@link Clock} in the business time zone.
 *
 * <p>Code never calls {@code now()} without this clock; lease and scheduling times that must agree
 * across nodes come from the database clock instead (AD-03).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * Returns the system clock in the configured business zone.
     *
     * @param properties reporting configuration
     * @return the application clock
     */
    @Bean
    public Clock clock(ReportingProperties properties) {
        return Clock.system(properties.getBusinessZone());
    }
}
