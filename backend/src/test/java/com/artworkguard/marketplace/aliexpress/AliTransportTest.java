package com.artworkguard.marketplace.aliexpress;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class AliTransportTest {
 @Test void acceptsOnlyExplicitHttpsImageHosts(){
  assertEquals("ae01.alicdn.com",AliTransport.imageUri("https://ae01.alicdn.com/kf/image.jpg").getHost());
  for(String url:List.of("http://ae01.alicdn.com/x","https://ae01.alicdn.com.evil.test/x","https://user@ae01.alicdn.com/x","https://ae01.alicdn.com:443/x","https://127.0.0.1/x","file:///etc/passwd","https://ae01.alicdn.com/x#fragment"))
   assertThrows(AliException.class,()->AliTransport.imageUri(url));
 }
 @Test void boundsResponseBytes()throws Exception{
  assertEquals(3,AliTransport.bounded(new ByteArrayInputStream(new byte[3]),3).length);
  assertThrows(AliException.class,()->AliTransport.bounded(new ByteArrayInputStream(new byte[4]),3));
 }
}
