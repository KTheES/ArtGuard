package com.artworkguard.marketplace.adapter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.artworkguard.marketplace.service.CatalogException;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;
@Component
public class MockImageLibrary {
 private final Map<String,byte[]> images=new HashMap<>();
 private final Map<String,MarketplaceListing.ImageListing> metadata=new HashMap<>();
 public MockImageLibrary(){
  for(String name:List.of("moon-cat-poster","moon-cat-shirt","forest-mug")){
   String key="mock-marketplace/images/"+name+".png";
   try(var input=new ClassPathResource(key).getInputStream()){
    byte[] bytes=input.readAllBytes();var image=ImageIO.read(new ByteArrayInputStream(bytes));
    if(image==null)throw new IllegalStateException("Invalid mock image");
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    images.put(key,bytes);
    metadata.put(name,new MarketplaceListing.ImageListing(name+"-image","https://mock.artworkguard.invalid/images/"+name+".png",key,image.getWidth(),image.getHeight(),hash));
   }catch(Exception e){throw new IllegalStateException("Could not load bundled mock image");}
  }
 }
 public MarketplaceListing.ImageListing metadata(String name){
  var value=metadata.get(name);if(value==null)throw CatalogException.notFound();return value;
 }
 public boolean matches(MarketplaceListing.ImageListing image){
  return metadata.values().stream().anyMatch(m->m.sourceKey().equals(image.sourceKey()) && m.width()==image.width() && m.height()==image.height() && m.imageHash().equals(image.imageHash()));
 }
 public byte[] read(String key){var bytes=images.get(key);if(bytes==null)throw CatalogException.notFound();return bytes.clone();}
}
