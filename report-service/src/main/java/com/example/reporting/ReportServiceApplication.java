package com.example.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Entry point of the FIPS Report Service.
 *
 * <p>Packaged as a WAR for an external Tomcat 9 ({@link SpringBootServletInitializer}); the
 * {@code main} method exists for local runs with {@code spring-boot:run}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ReportServiceApplication extends SpringBootServletInitializer {

    /**
     * Starts the application with an embedded container (local development only).
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ReportServiceApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ReportServiceApplication.class);
    }
}
