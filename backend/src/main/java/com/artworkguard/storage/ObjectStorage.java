package com.artworkguard.storage;
import java.time.Duration;
import java.util.Map;
public interface ObjectStorage {
 record UploadUrl(String url,Map<String,String> headers) {
  @Override public String toString(){return "UploadUrl[REDACTED]";}
 }
 UploadUrl uploadUrl(String key,String contentType,long size,Duration ttl);
 byte[] readUpload(String key,String contentType,long expectedSize);
 void putImage(String key,byte[] bytes);
 void putSource(String key,byte[] bytes);
 String downloadUrl(String key,Duration ttl);
}
