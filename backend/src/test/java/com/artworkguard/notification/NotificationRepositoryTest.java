package com.artworkguard.notification;
import com.artworkguard.detection.DetectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.mockito.Mockito.*;
class NotificationRepositoryTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final NotificationSettings settings=new NotificationSettings(false,"","https://app.example");
 final NotificationRepository repository=new NotificationRepository(jdbc,settings);
 @Test void mediumDetectionNeverQueuesEmail(){repository.enqueue(UUID.randomUUID(),UUID.randomUUID(),DetectionPolicy.Severity.MEDIUM);verifyNoInteractions(jdbc);}
 @Test void highDetectionUsesOnlyStoredEvidenceAndStableLink(){UUID detection=UUID.randomUUID(),run=UUID.randomUUID();when(jdbc.query(contains("evidence_snapshot"),any(RowMapper.class),eq(detection),eq(run))).thenReturn(List.of("작품: Source"));repository.enqueue(detection,run,DetectionPolicy.Severity.HIGH);verify(jdbc).update(contains("notification_delivery"),any(),eq(run),eq("HIGH"),contains("HIGH"),contains("https://app.example/detections/"+detection),eq(run),eq(detection));}
}
