package com.artworkguard.embedding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="artworkguard.embedding.enabled",havingValue="true")
public class EmbeddingListener {
 private final EmbeddingWorker worker;private final ObjectMapper mapper;
 public EmbeddingListener(EmbeddingWorker worker,ObjectMapper mapper){this.worker=worker;this.mapper=mapper;}
 @KafkaListener(topics="embedding.requested",groupId="artworkguard-embedding-v1",containerFactory="embeddingKafkaListenerContainerFactory")
 public void onEvent(String json){worker.process(EmbeddingEvent.parse(mapper,json));}
}
