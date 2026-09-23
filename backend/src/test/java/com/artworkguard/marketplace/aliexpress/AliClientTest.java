package com.artworkguard.marketplace.aliexpress;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AliClientTest {
 final ObjectMapper mapper=new ObjectMapper();
 static String valid(){return """
  {"aliexpress_affiliate_product_query_response":{"resp_result":{"resp_code":200,"result":{"current_record_count":1,
   "products":{"product":[{"product_id":100001,"shop_id":100,"product_title":"Test art","sale_price":"12.50","sale_price_currency":"USD",
   "product_main_image_url":"https://ae01.alicdn.com/kf/test.jpg"}]}}}}}
  """;}
 @Test void signatureMatchesIndependentUtf8GoldenVector(){
  var p=new HashMap<>(Map.of("app_key","test-key","method","aliexpress.affiliate.product.query","keywords","고양이 art","format","json","v","2.0","sign_method","md5","timestamp","2026-09-08 12:00:00"));
  assertEquals("65BF5D3A8EBA2C0872F504A7DE0294BC",AliClient.sign(p,"test-secret"));
  p.put("sign","old-signature");assertEquals("65BF5D3A8EBA2C0872F504A7DE0294BC",AliClient.sign(p,"test-secret"));
 }
 @Test void parsesProductAndExactDecimalPrice()throws Exception{
  var p=AliClient.parse(mapper.readTree(valid()),1).getFirst();
  assertEquals("100001",p.id());assertEquals("100",p.shopId());assertEquals(new java.math.BigDecimal("12.50"),p.price());
 }
 @Test void rejectsUpstreamErrorsMissingFieldsAndUnsafeImages()throws Exception{
  for(String body:List.of("{}",valid().replace("\"resp_code\":200","\"resp_code\":500"),valid().replace("ae01.alicdn.com","127.0.0.1"),valid().replace("\"12.50\"","\"NaN\""),valid().replace("\"shop_id\":100,",""))){
   var json=mapper.readTree(body);assertThrows(AliException.class,()->AliClient.parse(json,1));
  }
 }
 @Test void allowsExplicitEmptyResult()throws Exception{
  assertTrue(AliClient.parse(mapper.readTree("{\"aliexpress_affiliate_product_query_response\":{\"resp_result\":{\"resp_code\":200,\"result\":{\"current_record_count\":0}}}}"),1).isEmpty());
 }
 @Test void sendsSignedFormWithoutSecret(){
  var transport=mock(AliTransport.class);when(transport.post(any())).thenReturn(valid().getBytes(StandardCharsets.UTF_8));
  var settings=new AliSettings(true,"test-key","DO-NOT-TRANSMIT-SECRET","");
  var client=new AliClient(settings,transport,mapper,Clock.fixed(Instant.parse("2026-09-08T04:00:00Z"),ZoneOffset.UTC));
  client.search(new AliDtos.SearchRequest("cat & art",1,1));
  var capture=org.mockito.ArgumentCaptor.forClass(byte[].class);verify(transport).post(capture.capture());
  String body=new String(capture.getValue(),StandardCharsets.UTF_8);
  assertTrue(body.contains("keywords=cat+%26+art"));assertTrue(body.contains("sign="));assertFalse(body.contains("DO-NOT-TRANSMIT-SECRET"));
  assertFalse(settings.toString().contains("SECRET"));
 }
 @Test void disabledClientNeverCallsNetwork(){
  var transport=mock(AliTransport.class);
  var client=new AliClient(new AliSettings(false,"","",""),transport,mapper,Clock.systemUTC());
  assertThrows(AliException.class,()->client.search(new AliDtos.SearchRequest("cat",1,1)));verifyNoInteractions(transport);
 }
}
