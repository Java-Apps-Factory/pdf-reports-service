package com.example.reporting.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Turns on the pollers and background jobs, which register themselves as {@code SchedulingConfigurer}s, and
 * gives them one dedicated {@link ThreadPoolTaskScheduler} sized by {@code spring.task.scheduling.pool.size}
 * (6), so a slow job never delays the others (SPEC section 15). Boot does not create its own scheduler once
 * {@code SchedulingConfigurer} beans exist, hence the explicit bean. Set {@code reporting.jobs.enabled=false}
 * to run a node that only serves the API.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "reporting.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    /**
     * Creates the job scheduler from Boot's {@code spring.task.scheduling.*} settings.
     *
     * @param builder Boot's scheduler builder
     * @return the scheduler
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(TaskSchedulerBuilder builder) {
        return builder.build();
    }
}
