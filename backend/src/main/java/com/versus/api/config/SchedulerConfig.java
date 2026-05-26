package com.versus.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class SchedulerConfig {

    @Bean(name = "duelScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService duelScheduler() {
        AtomicInteger seq = new AtomicInteger();
        return Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "duel-scheduler-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
