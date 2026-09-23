package com.artworkguard.marketplace;
import com.artworkguard.marketplace.adapter.*;
import com.artworkguard.marketplace.domain.*;
import com.artworkguard.marketplace.service.*;
import com.artworkguard.product.dto.CatalogDtos.*;
import com.artworkguard.product.repository.CatalogRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MockCatalogTest {
 final MockImageLibrary images=new MockImageLibrary();
 final MockMarketplaceAdapter adapter=new MockMarketplaceAdapter(images);
 @Test void catalogSearchIsBoundedAndCaseInsensitive(){
  assertEquals(3,adapter.searchProducts("",20).size());
  assertEquals(2,adapter.searchProducts("MOON",20).size());
  assertEquals(1,adapter.searchProducts("moon",1).size());
  assertTrue(adapter.searchProducts("missing",20).isEmpty());
  assertThrows(IllegalArgumentException.class,()->adapter.searchProducts("",101));
  assertThrows(CatalogException.class,()->adapter.getProduct("missing"));
 }
 @Test void imageHashesMatchBundledPngAndReadsAreDefensive()throws Exception{
  for(var p:adapter.searchProducts("",20)){
   var m=p.images().getFirst(); byte[] bytes=images.read(m.sourceKey());
   assertEquals(m.imageHash(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
   var png=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
   assertEquals(m.width(),png.getWidth());assertEquals(m.height(),png.getHeight());
   bytes[0]=0;assertNotEquals(0,images.read(m.sourceKey())[0]);
  }
  assertThrows(CatalogException.class,()->images.read("../application.properties"));
 }
 @Test void rejectsDuplicateBatchBeforeAnyDatabaseWrites(){
  var repo=mock(CatalogRepository.class);
  try(var factory=Validation.buildDefaultValidatorFactory()){
   var importer=new CatalogImportService(repo,factory.getValidator(),images,mock(com.artworkguard.product.embedding.ProductEmbeddingJobService.class));
   var p=adapter.getProduct("forest-mug");
   assertThrows(CatalogException.class,()->importer.persist(MarketplaceCode.MOCK,List.of(p,p)));
   verifyNoInteractions(repo);
  }
 }
 @Test void persistsValidatedBatchAndReconcilesImages(){
  var repo=mock(CatalogRepository.class); var market=UUID.randomUUID();var seller=UUID.randomUUID();var product=UUID.randomUUID();
  when(repo.marketplace(MarketplaceCode.MOCK)).thenReturn(Optional.of(new Marketplace(market,MarketplaceCode.MOCK,"Mock","https://mock.artworkguard.invalid",true)));
  when(repo.upsertSeller(eq(market),any())).thenReturn(seller);
  when(repo.upsertProduct(eq(market),eq(seller),any())).thenReturn(product);
  try(var factory=Validation.buildDefaultValidatorFactory()){
   var result=new CatalogImportService(repo,factory.getValidator(),images,mock(com.artworkguard.product.embedding.ProductEmbeddingJobService.class)).persist(MarketplaceCode.MOCK,adapter.searchProducts("",20));
   assertEquals(3,result.upsertedProducts());assertEquals(3,result.upsertedImages());
   verify(repo,times(3)).upsertImage(eq(product),any());
   verify(repo,times(3)).deactivateMissingImages(eq(product),anyList());
  }
 }
 @Test void disabledCollectionDoesNotReachAdapter(){
  var access=mock(CatalogAccess.class);var mockAdapter=mock(MockMarketplaceAdapter.class);var importer=mock(CatalogImportService.class);var id=UUID.randomUUID();
  assertThrows(CatalogException.class,()->new MockCollectionService(access,mock(RedisCollectionCoordinator.class),false).collect(id,new CollectRequest(null,null)));
  verify(access).admin(id);verifyNoInteractions(mockAdapter,importer);
 }
 @Test void databaseRoleAndStatusAreRechecked(){
  var users=mock(com.artworkguard.user.repository.UserRepository.class);var user=mock(com.artworkguard.user.domain.AppUser.class);var id=UUID.randomUUID();
  when(users.findById(id)).thenReturn(Optional.of(user));when(user.isActive()).thenReturn(true);
  when(user.getRole()).thenReturn(com.artworkguard.user.domain.AppUser.Role.ROLE_USER);
  var access=new CatalogAccess(users);assertThrows(CatalogException.class,()->access.admin(id));
  when(user.getRole()).thenReturn(com.artworkguard.user.domain.AppUser.Role.ROLE_ADMIN);assertDoesNotThrow(()->access.admin(id));
  when(user.isActive()).thenReturn(false);assertThrows(com.artworkguard.auth.service.AuthException.class,()->access.admin(id));
 }
 @Test void searchWildcardsAreLiteral(){
  assertEquals("50!%!_!!",CatalogRepository.escapeSearch("50%_!"));
 }
}
