package com.example.hbsz_idc_system_end.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 * <p>
 * 为 {@link java.util.concurrent.CompletableFuture#supplyAsync} 提供专用线程池，
 * 避免占用 {@link java.util.concurrent.ForkJoinPool#commonPool()} 导致其他并行任务饥饿。
 * <p>
 * 应用场景：统计报表并行导出（5 项独立查询并发执行）、日报并行生成等。
 *
 * @author hbsz
 */
@Configuration
public class AsyncConfig {

    /**
     * 统计导出专用线程池。
     * <p>
     * 核心线程数 5（对应 5 项统计查询），最大 10（允许突发），
     * 队列容量 50（防止 OOM），拒绝策略 CallerRunsPolicy（队列满时降级为同步执行）。
     *
     * @return 统计导出线程池
     */
    @Bean("statisticsExecutor")
    public Executor statisticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("stats-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
