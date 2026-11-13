package com.example.reporting.config;

import com.example.reporting.profile.ReportingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Worker thread pools (SPEC section 15). The generation pool has core = max = {@code generation.workers} and
 * no queue: the poller never claims more documents than there are free threads. On shutdown it waits up to
 * 60 s; unfinished work is recovered by lease expiry.
 */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfig {

    private static final int SHUTDOWN_WAIT_SECONDS = 60;

    /**
     * Creates the generation pool.
     *
     * @param properties reporting configuration
     * @return the executor
     */
    @Bean
    public ThreadPoolTaskExecutor generationExecutor(ReportingProperties properties) {
        int workers = properties.getGeneration().getWorkers();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("generation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
