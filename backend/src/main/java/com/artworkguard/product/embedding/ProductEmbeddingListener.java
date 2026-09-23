package com.artworkguard.product.embedding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="artworkguard.embedding.enabled",havingValue="true")
public class ProductEmbeddingListener {
 private final ProductEmbeddingWorker worker;private final ObjectMapper mapper;
 public ProductEmbeddingListener(ProductEmbeddingWorker worker,ObjectMapper mapper){this.worker=worker;this.mapper=mapper;}
 @KafkaListener(topics="product.embedding.requested",groupId="artworkguard-product-embedding-v1",containerFactory="productEmbeddingKafkaListenerContainerFactory")
 public void onEvent(String json){worker.process(ProductEmbeddingEvent.parse(mapper,json));}
}
