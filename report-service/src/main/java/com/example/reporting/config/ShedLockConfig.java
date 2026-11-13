package com.example.reporting.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ShedLock over the {@code SHEDLOCK} table, using database time (AD-03). Jobs with dynamic lock names use the
 * {@link LockingTaskExecutor} directly instead of {@code @SchedulerLock}.
 */
@Configuration(proxyBeanMethods = false)
public class ShedLockConfig {

    /**
     * Creates the JDBC lock provider.
     *
     * @param dataSource application data source
     * @return lock provider
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("SHEDLOCK")
                .usingDbTime()
                .build());
    }

    /**
     * Creates the executor that runs a task only while holding its lock.
     *
     * @param lockProvider lock provider
     * @return locking executor
     */
    @Bean
    public LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
        return new DefaultLockingTaskExecutor(lockProvider);
    }
}
