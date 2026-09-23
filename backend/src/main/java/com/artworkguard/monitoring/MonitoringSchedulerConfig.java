package com.artworkguard.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "artworkguard.monitor.enabled", havingValue = "true")
public class MonitoringSchedulerConfig {
}
