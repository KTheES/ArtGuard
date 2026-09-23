package com.artworkguard.storage;
import com.artworkguard.artwork.service.ArtworkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
public class S3ObjectStorage implements ObjectStorage {
 private final S3Client client;
 private final S3Presigner presigner;
 private final String bucket;
 public S3ObjectStorage(S3Client client,S3Presigner presigner,String bucket){this.client=client;this.presigner=presigner;this.bucket=bucket;}
 public UploadUrl uploadUrl(String key,String contentType,long size,Duration ttl) {
  try {
   var put=PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength(size).build();
   var signed=presigner.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build());
   Map<String,String> headers=new LinkedHashMap<>();
   signed.signedHeaders().forEach((k,v)->{if(!k.equalsIgnoreCase("host"))headers.put(k,String.join(",",v));});
   return new UploadUrl(signed.url().toString(),Map.copyOf(headers));
  } catch(SdkException e){throw ArtworkException.storageUnavailable();}
 }
 public byte[] readUpload(String key,String contentType,long expectedSize) {
  try {
   var head=client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
   if(head.contentLength()!=expectedSize || expectedSize>20971520 || !contentType.equals(head.contentType()))throw ArtworkException.invalidImage();
   try(var stream=client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).ifMatch(head.eTag()).build())) {
    byte[] bytes=stream.readNBytes((int)expectedSize+1);
    if(bytes.length!=expectedSize)throw ArtworkException.invalidImage();
    return bytes;
   }
  } catch(S3Exception e) {
   if(e.statusCode()==404 || e.statusCode()==412)throw ArtworkException.invalidImage();
   throw ArtworkException.storageUnavailable();
  } catch(SdkException|IOException e){throw ArtworkException.storageUnavailable();}
 }
 public void putImage(String key,byte[] bytes) {
  try {client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/png").build(),RequestBody.fromBytes(bytes));}
  catch(SdkException e){throw ArtworkException.storageUnavailable();}
 }
 public String downloadUrl(String key,Duration ttl) {
  try {
   var get=GetObjectRequest.builder().bucket(bucket).key(key).responseContentType("image/png").responseCacheControl("private, no-store").build();
   return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build()).url().toString();
  } catch(SdkException e){throw ArtworkException.storageUnavailable();}
 }
 public void putSource(String key,byte[] bytes){
  try{client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/octet-stream").build(),RequestBody.fromBytes(bytes));}
  catch(SdkException e){throw ArtworkException.storageUnavailable();}
 }
}
