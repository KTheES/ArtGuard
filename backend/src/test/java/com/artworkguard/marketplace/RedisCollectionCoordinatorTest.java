package com.artworkguard.marketplace;
import com.artworkguard.redis.*;
import com.artworkguard.marketplace.service.*;
import com.artworkguard.marketplace.adapter.*;
import com.artworkguard.marketplace.domain.*;
import com.artworkguard.product.dto.CatalogDtos.*;
import com.artworkguard.product.domain.Product;
import com.artworkguard.product.repository.CatalogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RedisCollectionCoordinatorTest {
 final RedisWorkGuard guard=mock(RedisWorkGuard.class);final MockMarketplaceAdapter adapter=mock(MockMarketplaceAdapter.class);
 final CatalogImportService importer=mock(CatalogImportService.class);final CatalogRepository catalog=mock(CatalogRepository.class);
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 final RedisCollectionCoordinator coordinator=new RedisCollectionCoordinator(guard,adapter,importer,catalog,mapper);
 final UUID actor=UUID.randomUUID(),productId=UUID.randomUUID();
 final MarketplaceListing listing=new MockMarketplaceAdapter(new MockImageLibrary()).getProduct("forest-mug");
 @BeforeEach void setup(){
  when(catalog.marketplace(MarketplaceCode.MOCK)).thenReturn(Optional.of(new Marketplace(UUID.randomUUID(),MarketplaceCode.MOCK,"Mock","https://mock.artworkguard.invalid",true)));
  when(guard.acquire(anyString())).thenReturn("token");
  when(adapter.searchProducts(anyString(),anyInt())).thenReturn(List.of(listing));
  when(importer.persist(eq(MarketplaceCode.MOCK),anyList())).thenReturn(new CollectResponse(MarketplaceCode.MOCK,1,1,List.of(productId)));
 }
 @Test void cacheHitDoesNotCrawlOrPersist()throws Exception{
  when(guard.read(contains("search:"))).thenReturn(mapper.writeValueAsString(new CollectResponse(MarketplaceCode.MOCK,1,1,List.of(productId))));
  var result=coordinator.collect(actor,new CollectRequest("FOREST",20));
  assertTrue(result.cached());assertEquals(0,result.upsertedProducts());verifyNoInteractions(adapter,importer);
  verify(guard,never()).acquire(anyString());
 }
 @Test void databaseCommitPrecedesCachePublication(){
  var result=coordinator.collect(actor,new CollectRequest("forest",20));assertFalse(result.cached());assertEquals(1,result.upsertedProducts());
  var order=inOrder(guard,importer);
  order.verify(guard).renew(anyString(),eq("token"));
  order.verify(importer).persist(eq(MarketplaceCode.MOCK),anyList());
  order.verify(guard).publish(anyString(),eq("token"),anyString(),anyString(),anyMap());
  verify(guard).release(anyString(),eq("token"));
 }
 @Test void databaseFailureDoesNotPoisonCache(){
  when(importer.persist(any(),anyList())).thenThrow(new IllegalStateException("failed"));
  assertThrows(IllegalStateException.class,()->coordinator.collect(actor,new CollectRequest("",20)));
  verify(guard,never()).publish(anyString(),anyString(),anyString(),anyString(),anyMap());
  verify(guard).release(anyString(),eq("token"));
 }
 @Test void busyMarketDoesNotCrawl(){
  when(guard.acquire(anyString())).thenThrow(new RedisGuardException(org.springframework.http.HttpStatus.CONFLICT,"COLLECTION_IN_PROGRESS",2));
  assertThrows(RedisGuardException.class,()->coordinator.collect(actor,new CollectRequest("",20)));
  verifyNoInteractions(adapter,importer);
 }
 @Test void identicalProductInAnotherSearchSkipsUpsert()throws Exception{
  String fingerprint=RedisCollectionCoordinator.hash(mapper.writeValueAsString(listing));
  when(guard.read(contains("product:"))).thenReturn(mapper.writeValueAsString(new RedisCollectionCoordinator.Marker(fingerprint,productId)));
  var product=mock(Product.class);when(product.externalProductId()).thenReturn("forest-mug");when(product.status()).thenReturn("ACTIVE");
  when(catalog.product(productId)).thenReturn(Optional.of(new CatalogRepository.CatalogRow(product,MarketplaceCode.MOCK,null)));
  when(importer.persist(MarketplaceCode.MOCK,List.of())).thenReturn(new CollectResponse(MarketplaceCode.MOCK,0,0,List.of()));
  var result=coordinator.collect(actor,new CollectRequest("mug",20));
  assertEquals(1,result.deduplicatedProducts());assertEquals(0,result.upsertedProducts());assertEquals(List.of(productId),result.productIds());
  verify(importer).persist(MarketplaceCode.MOCK,List.of());
 }
 @Test void malformedCacheIsTreatedAsMiss(){
  when(guard.read(contains("search:"))).thenReturn("{}");
  assertEquals(1,coordinator.collect(actor,new CollectRequest("",20)).upsertedProducts());
 }
}
