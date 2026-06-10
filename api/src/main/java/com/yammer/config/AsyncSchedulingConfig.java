package com.yammer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the periodic fiscal-outbox dispatcher ({@code @Scheduled}) and async handling
 * of bridge-ready events ({@code @Async}).
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AsyncSchedulingConfig {
}
