package com.artworkguard.product.embedding;
import com.artworkguard.embedding.AiEmbeddingClient;
import com.artworkguard.marketplace.adapter.MockImageLibrary;
import com.artworkguard.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SuppressWarnings("unchecked")
class ProductEmbeddingWorkerTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final ObjectStorage storage=mock(ObjectStorage.class);
 final AiEmbeddingClient ai=mock(AiEmbeddingClient.class);final MockImageLibrary images=new MockImageLibrary();
 final ProductEmbeddingWorker worker=new ProductEmbeddingWorker(jdbc,storage,ai,images);
 final UUID jobId=UUID.randomUUID(),imageId=UUID.randomUUID();
 final ProductEmbeddingEvent event=ProductEmbeddingEvent.create(jobId,imageId,1);
 final String hash=images.metadata("forest-mug").imageHash();
 AiEmbeddingClient.RegionSet result(){float[] vector=new float[768];vector[0]=1;String p="0123456789abcdef",d="fedcba9876543210";return new AiEmbeddingClient.RegionSet("fixed-overlap-5-v1",vector,"phash32-dhash9-luma-v1",p,d,List.of(
  new AiEmbeddingClient.RegionVector("CENTER",.175,.175,.65,.65,vector,p,d),new AiEmbeddingClient.RegionVector("TOP_LEFT",0,0,.65,.65,vector,p,d),
  new AiEmbeddingClient.RegionVector("TOP_RIGHT",.35,0,.65,.65,vector,p,d),new AiEmbeddingClient.RegionVector("BOTTOM_LEFT",0,.35,.65,.65,vector,p,d),
  new AiEmbeddingClient.RegionVector("BOTTOM_RIGHT",.35,.35,.65,.65,vector,p,d)));}
 void setup(String status,int generation,String sourceHash,boolean active){
  when(jdbc.query(anyString(),any(RowMapper.class),eq(imageId))).thenReturn(List.of(new ProductEmbeddingWorker.Image(sourceHash,"MOCK_RESOURCE","mock-marketplace/images/forest-mug.png",active)));
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId))).thenReturn(List.of(new ProductEmbeddingWorker.Job(imageId,hash,status,generation)));
 }
 @Test void completedDuplicateDoesNotUploadOrCallAi(){setup("COMPLETED",1,hash,true);worker.process(event);verifyNoInteractions(storage,ai);}
 @Test void staleGenerationDoesNotUploadOrCallAi(){setup("QUEUED",2,hash,true);worker.process(event);verifyNoInteractions(storage,ai);}
 @Test void changedImageCancelsOldJob(){setup("QUEUED",1,"a".repeat(64),true);worker.process(event);verifyNoInteractions(storage,ai);verify(jdbc).update(contains("CANCELED"),eq(jobId));}
 @Test void inactiveImageCancelsJob(){setup("QUEUED",1,hash,false);worker.process(event);verifyNoInteractions(storage,ai);verify(jdbc).update(contains("CANCELED"),eq(jobId));}
 @Test void mismatchedImageIdIsRejected(){
  setup("QUEUED",1,hash,true);
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId))).thenReturn(List.of(new ProductEmbeddingWorker.Job(UUID.randomUUID(),hash,"QUEUED",1)));
  assertThrows(IllegalArgumentException.class,()->worker.process(event));verifyNoInteractions(storage,ai);
 }
 @Test void fixtureHashMismatchDoesNotUpload(){
  setup("QUEUED",1,"b".repeat(64),true);
  when(jdbc.query(anyString(),any(RowMapper.class),eq(jobId))).thenReturn(List.of(new ProductEmbeddingWorker.Job(imageId,"b".repeat(64),"QUEUED",1)));
  assertThrows(IllegalArgumentException.class,()->worker.process(event));verifyNoInteractions(storage,ai);
 }
 @Test void successfulProcessingUploadsImmutableKeyAndCommitsVectorAndStatus(){
  setup("QUEUED",1,hash,true);String key="product-images/"+imageId+"/"+hash+".png";
  when(storage.downloadUrl(eq(key),any())).thenReturn("https://storage.test/private");
  when(ai.embedRegions(anyString())).thenReturn(result());
  when(jdbc.update(contains("INSERT INTO product_image_embedding"),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
  when(jdbc.query(contains("SELECT id FROM product_image_embedding"),any(RowMapper.class),eq(jobId),eq(imageId),eq(hash))).thenReturn(List.of(UUID.randomUUID()));
  worker.process(event);
  verify(storage).putImage(eq(key),aryEq(images.read("mock-marketplace/images/forest-mug.png")));
  verify(ai).embedRegions("https://storage.test/private");
  verify(jdbc,times(5)).update(contains("product_image_region_embedding"),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
  verify(jdbc).update(contains("DELETE FROM detection_signal"),any(UUID.class));
  verify(jdbc).update(contains("INSERT INTO detection_signal"),any(UUID.class));
  verify(jdbc).update(contains("storage_key"),eq(key),eq(images.read("mock-marketplace/images/forest-mug.png").length),eq(imageId),eq(hash));
  verify(jdbc).update(contains("COMPLETED"),eq(jobId));
 }
 private static byte[] aryEq(byte[] bytes){return org.mockito.AdditionalMatchers.aryEq(bytes);}
 @Test void aiFailureDoesNotMarkCompleted(){
  setup("QUEUED",1,hash,true);when(storage.downloadUrl(anyString(),any())).thenReturn("https://storage.test/private");
  when(ai.embedRegions(anyString())).thenThrow(new IllegalStateException("offline"));
  assertThrows(IllegalStateException.class,()->worker.process(event));
  verify(jdbc,never()).update(contains("COMPLETED"),eq(jobId));
 }
 @Test void storedProductImageIsReadFromS3BeforeEmbedding(){
  setup("QUEUED",1,hash,true);
  byte[] bytes=images.read("mock-marketplace/images/forest-mug.png");
  String source="catalog-imports/aliexpress/"+hash+".png";
  when(jdbc.query(anyString(),any(RowMapper.class),eq(imageId))).thenReturn(List.of(new ProductEmbeddingWorker.Image(hash,"STORED_PNG",source,true,bytes.length)));
  when(storage.readUpload(source,"image/png",bytes.length)).thenReturn(bytes);
  when(storage.downloadUrl(anyString(),any())).thenReturn("https://storage.test/private");
  when(ai.embedRegions(anyString())).thenReturn(result());
  when(jdbc.update(contains("INSERT INTO product_image_embedding"),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
  when(jdbc.query(contains("SELECT id FROM product_image_embedding"),any(RowMapper.class),eq(jobId),eq(imageId),eq(hash))).thenReturn(List.of(UUID.randomUUID()));
  worker.process(event);verify(storage).readUpload(source,"image/png",bytes.length);
  verify(jdbc).update(contains("COMPLETED"),eq(jobId));
 }
 @Test void unexpectedStoredKeyIsRejectedBeforeStorageAccess(){
  setup("QUEUED",1,hash,true);
  when(jdbc.query(anyString(),any(RowMapper.class),eq(imageId))).thenReturn(List.of(new ProductEmbeddingWorker.Image(hash,"STORED_PNG","private-user-image",true,10)));
  assertThrows(IllegalArgumentException.class,()->worker.process(event));verifyNoInteractions(storage,ai);
 }}
