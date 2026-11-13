package com.example.reporting.config;

import com.example.reporting.profile.ReportingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Worker thread pools (SPEC section 15): core = max = the configured workers and no queue, so a poller never
 * claims more documents than there are free threads. On shutdown it waits up to
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
        return pool(properties.getGeneration().getWorkers(), "generation-");
    }

    /**
     * Creates the send pool, sized like the send semaphore ({@code transmission.workers}, default 2).
     *
     * @param properties reporting configuration
     * @return the executor
     */
    @Bean
    public ThreadPoolTaskExecutor sendExecutor(ReportingProperties properties) {
        return pool(properties.getTransmission().getWorkers(), "send-");
    }

    private static ThreadPoolTaskExecutor pool(int workers, String prefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
