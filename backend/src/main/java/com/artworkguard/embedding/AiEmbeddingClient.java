package com.artworkguard.embedding;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.*;
@Component
@ConditionalOnProperty(name="artworkguard.embedding.enabled",havingValue="true")
public class AiEmbeddingClient {
 private final RestClient client;
 private final com.artworkguard.common.resilience.CallCircuit circuit=new com.artworkguard.common.resilience.CallCircuit(5,Duration.ofSeconds(30),java.time.Clock.systemUTC());
 public AiEmbeddingClient(@Value("${artworkguard.embedding.ai-url}") String url,@Value("${artworkguard.embedding.api-key}") String key) {
  if(key.length()<32)throw new IllegalArgumentException("AI_API_KEY must contain at least 32 characters");
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
  factory.setReadTimeout(Duration.ofSeconds(90));
  this.client=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-API-Key",key).build();
 }
 public float[] embed(String imageUrl) {
  return embedWithHashes(imageUrl).embedding();
 }
 public record EmbeddingResult(float[] embedding,String perceptualHashVersion,String pHash,String dHash){}
 public EmbeddingResult embedWithHashes(String imageUrl) {
  JsonNode response;
  try{response=circuit.call(()->client.post().uri("/v1/embeddings").body(Map.of("imageUrl",imageUrl)).retrieve().body(JsonNode.class));}
  catch(Exception e){throw new IllegalStateException("AI embedding request failed");}
  return new EmbeddingResult(validate(response),hashVersion(response),hash(response,"pHash"),hash(response,"dHash"));
 }
 public record RegionVector(String key,double x,double y,double width,double height,float[] embedding,String pHash,String dHash){
  public RegionVector(String key,double x,double y,double width,double height,float[] embedding){this(key,x,y,width,height,embedding,null,null);}
 }
 public record RegionSet(String scheme,float[] full,String perceptualHashVersion,String pHash,String dHash,List<RegionVector> regions){
  public RegionSet(String scheme,float[] full,List<RegionVector> regions){this(scheme,full,"phash32-dhash9-luma-v1",null,null,regions);}
 }
 public RegionSet embedRegions(String imageUrl) {
  JsonNode response;
  try{response=circuit.call(()->client.post().uri("/v1/region-embeddings").body(Map.of("imageUrl",imageUrl)).retrieve().body(JsonNode.class));}
  catch(Exception e){throw new IllegalStateException("AI region embedding request failed");}
  float[] full=validate(response);
  if(!"fixed-overlap-5-v1".equals(response.path("regionScheme").asText()) || !response.path("regions").isArray() || response.path("regions").size()!=5)
   throw new IllegalArgumentException("Invalid region embedding scheme");
  var seen=new HashSet<String>();var regions=new ArrayList<RegionVector>();
  for(JsonNode item:response.path("regions")){
   String key=item.path("key").asText();double x=item.path("x").asDouble(-1),y=item.path("y").asDouble(-1);
   double width=item.path("width").asDouble(-1),height=item.path("height").asDouble(-1);
   if(!Set.of("CENTER","TOP_LEFT","TOP_RIGHT","BOTTOM_LEFT","BOTTOM_RIGHT").contains(key) || !seen.add(key)
    || x<0 || y<0 || width<=0 || height<=0 || x+width>1.000001 || y+height>1.000001)
    throw new IllegalArgumentException("Invalid image region metadata");
   var copy=((com.fasterxml.jackson.databind.node.ObjectNode)response.deepCopy());copy.set("embedding",item.path("embedding"));
   regions.add(new RegionVector(key,x,y,width,height,validate(copy),hash(item,"pHash"),hash(item,"dHash")));
  }
  return new RegionSet(response.path("regionScheme").asText(),full,hashVersion(response),hash(response,"pHash"),hash(response,"dHash"),List.copyOf(regions));
 }
 private static String hashVersion(JsonNode response){if(!"phash32-dhash9-luma-v1".equals(response.path("perceptualHashVersion").asText()))throw new IllegalArgumentException("Invalid perceptual hash version");return response.path("perceptualHashVersion").asText();}
 private static String hash(JsonNode response,String field){String value=response.path(field).asText();if(!value.matches("^[a-f0-9]{16}$"))throw new IllegalArgumentException("Invalid perceptual hash");return value;}
 public static float[] validate(JsonNode response) {
  if(response==null || !"dinov2".equals(response.path("model").asText()) || !EmbeddingModel.ID.equals(response.path("modelId").asText())
   || !EmbeddingModel.VERSION.equals(response.path("version").asText()) || !EmbeddingModel.PREPROCESSING.equals(response.path("preprocessingVersion").asText())
   || response.path("dimension").asInt()!=EmbeddingModel.DIMENSION || !response.path("normalized").asBoolean()
   || !response.path("embedding").isArray() || response.path("embedding").size()!=EmbeddingModel.DIMENSION)
   throw new IllegalArgumentException("Invalid embedding model or vector shape");
  float[] values=new float[EmbeddingModel.DIMENSION];double norm=0;
  for(int i=0;i<values.length;i++){
   var node=response.path("embedding").get(i);
   if(!node.isNumber())throw new IllegalArgumentException("Invalid embedding value");
   values[i]=(float)node.asDouble();
   if(!Float.isFinite(values[i]))throw new IllegalArgumentException("Non-finite embedding value");
   norm+=(double)values[i]*values[i];
  }
  if(Math.abs(norm-1.0)>0.002)throw new IllegalArgumentException("Embedding must be unit normalized");
  return values;
 }
}
