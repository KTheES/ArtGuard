package com.artworkguard.product.embedding;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ProductEmbeddingEventTest {
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 @Test void eventRoundTripsWithoutStorageCredentials()throws Exception{
  var event=ProductEmbeddingEvent.create(UUID.randomUUID(),UUID.randomUUID(),1);
  String json=mapper.writeValueAsString(event);assertEquals(event,ProductEmbeddingEvent.parse(mapper,json));
  assertFalse(json.contains("imageUrl"));assertFalse(json.contains("storageKey"));
 }
 @Test void rejectsArtworkEventsAndInvalidGeneration()throws Exception{
  String json=mapper.writeValueAsString(ProductEmbeddingEvent.create(UUID.randomUUID(),UUID.randomUUID(),1));
  assertThrows(IllegalArgumentException.class,()->ProductEmbeddingEvent.parse(mapper,json.replace("PRODUCT_EMBEDDING_REQUESTED","EMBEDDING_REQUESTED")));
  assertThrows(IllegalArgumentException.class,()->ProductEmbeddingEvent.parse(mapper,json.replace("\"generation\":1","\"generation\":0")));
  assertThrows(IllegalArgumentException.class,()->ProductEmbeddingEvent.parse(mapper,"{}"));
 }
}
