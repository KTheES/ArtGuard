package com.artworkguard.marketplace.aliexpress;
import com.artworkguard.marketplace.service.*;
import com.artworkguard.marketplace.domain.*;
import com.artworkguard.product.repository.CatalogRepository;
import com.artworkguard.product.dto.CatalogDtos.CollectResponse;
import com.artworkguard.artwork.service.ImageValidator;
import com.artworkguard.storage.ObjectStorage;
import com.artworkguard.redis.RedisWorkGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AliCollectionServiceTest {
 final CatalogAccess access=mock(CatalogAccess.class);final RedisWorkGuard guard=mock(RedisWorkGuard.class);
 final AliClient client=mock(AliClient.class);final AliTransport transport=mock(AliTransport.class);final ImageValidator images=mock(ImageValidator.class);
 final ObjectStorage storage=mock(ObjectStorage.class);final AliImporter importer=mock(AliImporter.class);final CatalogRepository catalog=mock(CatalogRepository.class);
 AliCollectionService service(boolean enabled){return new AliCollectionService(access,new AliSettings(enabled,"key","secret",""),guard,client,transport,images,storage,importer,new ObjectMapper(),catalog);}
 @BeforeEach void setup(){
  when(catalog.marketplace(MarketplaceCode.ALIEXPRESS)).thenReturn(Optional.of(new Marketplace(UUID.randomUUID(),MarketplaceCode.ALIEXPRESS,"Ali","https://www.aliexpress.com",true)));
  when(guard.acquire(anyString())).thenReturn("token");
 }
 @Test void disabledCollectionDoesNotCallApi(){assertThrows(AliException.class,()->service(false).collect(UUID.randomUUID(),new AliDtos.SearchRequest("art",1,1)));verifyNoInteractions(client,transport,storage,importer);}
 @Test void failedImageUploadNeverCommitsDatabase(){
  var listing=new AliDtos.Listing("1","Art","2",new java.math.BigDecimal("1.00"),"USD","https://ae01.alicdn.com/x.png");
  when(client.search(any())).thenReturn(List.of(listing));
  when(transport.image(anyString())).thenReturn(new AliTransport.ImageBytes(new byte[]{1},"image/png"));
  when(images.validate(any(),anyString())).thenReturn(new ImageValidator.ValidatedImage(new byte[]{1},new byte[]{1},1,1));
  doThrow(new IllegalStateException()).when(storage).putImage(anyString(),any());
  assertThrows(IllegalStateException.class,()->service(true).collect(UUID.randomUUID(),new AliDtos.SearchRequest("art",1,1)));
  verifyNoInteractions(importer);verify(guard).release(anyString(),eq("token"));
 }
 @Test void databaseCommitFollowsStorageUpload(){
  var listing=new AliDtos.Listing("1","Art","2",new java.math.BigDecimal("1.00"),"USD","https://ae01.alicdn.com/x.png");
  when(client.search(any())).thenReturn(List.of(listing));when(transport.image(anyString())).thenReturn(new AliTransport.ImageBytes(new byte[]{1},"image/png"));
  when(images.validate(any(),anyString())).thenReturn(new ImageValidator.ValidatedImage(new byte[]{1},new byte[]{1},1,1));
  when(importer.persist(anyList())).thenReturn(new CollectResponse(MarketplaceCode.ALIEXPRESS,1,1,List.of(UUID.randomUUID())));
  service(true).collect(UUID.randomUUID(),new AliDtos.SearchRequest("art",1,1));
  var order=inOrder(storage,importer,guard);
  order.verify(storage).putImage(startsWith("catalog-imports/aliexpress/"),any());
  order.verify(importer).persist(anyList());order.verify(guard).publish(anyString(),anyString(),anyString(),anyString(),anyMap());
 }
}
