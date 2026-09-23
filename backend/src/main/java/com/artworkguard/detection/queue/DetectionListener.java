package com.artworkguard.detection.queue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="artworkguard.detection.enabled",havingValue="true")
public class DetectionListener {
 private final DetectionWorker worker;private final ObjectMapper mapper;
 public DetectionListener(DetectionWorker worker,ObjectMapper mapper){this.worker=worker;this.mapper=mapper;}
 @KafkaListener(topics="detection.requested",groupId="artworkguard-detection-v1",containerFactory="detectionKafkaListenerContainerFactory")
 public void onEvent(String json){worker.process(DetectionEvent.parse(mapper,json));}
}
