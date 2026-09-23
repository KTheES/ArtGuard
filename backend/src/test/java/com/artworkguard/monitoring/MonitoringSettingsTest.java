package com.artworkguard.monitoring;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
class MonitoringSettingsTest {
 @Test void acceptsDailyDefaults(){var settings=new MonitoringSettings(false,Duration.ofDays(1),100,100);assertEquals(100,settings.batchSize());}
 @Test void rejectsUnsafeBounds(){
  assertThrows(IllegalArgumentException.class,()->new MonitoringSettings(true,Duration.ofMinutes(4),100,100));
  assertThrows(IllegalArgumentException.class,()->new MonitoringSettings(true,Duration.ofDays(32),100,100));
  assertThrows(IllegalArgumentException.class,()->new MonitoringSettings(true,Duration.ofHours(1),0,100));
  assertThrows(IllegalArgumentException.class,()->new MonitoringSettings(true,Duration.ofHours(1),100,501));
 }
}
