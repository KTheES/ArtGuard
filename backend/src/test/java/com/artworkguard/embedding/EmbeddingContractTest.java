package com.artworkguard.embedding;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class EmbeddingContractTest {
 ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 ObjectNode valid() {
  var node=mapper.createObjectNode().put("model","dinov2").put("modelId",EmbeddingModel.ID)
   .put("version",EmbeddingModel.VERSION).put("preprocessingVersion",EmbeddingModel.PREPROCESSING)
   .put("dimension",768).put("normalized",true);
  var values=node.putArray("embedding");values.add(1.0);for(int i=1;i<768;i++)values.add(0.0);return node;
 }
 @Test void acceptsExpectedUnitVector(){assertThat(AiEmbeddingClient.validate(valid())).hasSize(768);}
 @Test void rejectsWrongModelVersionAndPreprocessing(){
  for(String field:java.util.List.of("model","modelId","version","preprocessingVersion")){
   var node=valid();node.put(field,"wrong");
   assertThatThrownBy(()->AiEmbeddingClient.validate(node)).isInstanceOf(IllegalArgumentException.class);
  }
 }
 @Test void rejectsWrongShapeAndNonUnitVector(){
  var node=valid();node.withArray("embedding").remove(0);
  assertThatThrownBy(()->AiEmbeddingClient.validate(node)).isInstanceOf(IllegalArgumentException.class);
  var nonunit=valid();nonunit.withArray("embedding").set(0,DoubleNode.valueOf(2.0));
  assertThatThrownBy(()->AiEmbeddingClient.validate(nonunit)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void rejectsNanAndNonNumericValues(){
  for(JsonNode bad:java.util.List.of(DoubleNode.valueOf(Double.NaN),TextNode.valueOf("secret"))){
   var node=valid();node.withArray("embedding").set(0,bad);
   assertThatThrownBy(()->AiEmbeddingClient.validate(node)).isInstanceOf(IllegalArgumentException.class);
  }
 }
 @Test void eventRoundTripCarriesVersionTraceAndGeneration()throws Exception{
  var event=EmbeddingEvent.create(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),2);
  assertThat(EmbeddingEvent.parse(mapper,mapper.writeValueAsString(event))).isEqualTo(event);
 }
 @Test void malformedEventDoesNotEchoPayload(){
  assertThatThrownBy(()->EmbeddingEvent.parse(mapper,"{\"secret\":\"signed-url-token\"}"))
   .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid embedding event");
 }
}
