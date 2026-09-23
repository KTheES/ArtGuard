package com.artworkguard.monitoring;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class MonitoringSettings {
 private final boolean enabled;private final Duration interval;private final int batchSize,resultLimit;
 public MonitoringSettings(@Value("${artworkguard.monitor.enabled:false}") boolean enabled,
  @Value("${artworkguard.monitor.interval:PT24H}") Duration interval,
  @Value("${artworkguard.monitor.batch-size:100}") int batchSize,@Value("${artworkguard.monitor.result-limit:100}") int resultLimit){
  if(interval.compareTo(Duration.ofMinutes(5))<0||interval.compareTo(Duration.ofDays(31))>0)throw new IllegalArgumentException("Monitoring interval must be between 5 minutes and 31 days");
  if(batchSize<1||batchSize>500)throw new IllegalArgumentException("Monitoring batch size must be between 1 and 500");
  if(resultLimit<1||resultLimit>500)throw new IllegalArgumentException("Monitoring result limit must be between 1 and 500");
  this.enabled=enabled;this.interval=interval;this.batchSize=batchSize;this.resultLimit=resultLimit;
 }
 public boolean enabled(){return enabled;}public Duration interval(){return interval;}public int batchSize(){return batchSize;}public int resultLimit(){return resultLimit;}
}
