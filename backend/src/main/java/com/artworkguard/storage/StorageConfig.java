package com.artworkguard.storage;
import com.artworkguard.artwork.service.ArtworkException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.net.URI;
import java.time.Duration;
@Configuration
public class StorageConfig {
 @Bean @ConditionalOnProperty(name="artworkguard.storage.enabled",havingValue="false",matchIfMissing=true)
 ObjectStorage disabledStorage() {
  return new ObjectStorage() {
   public UploadUrl uploadUrl(String k,String c,long s,Duration t){throw ArtworkException.storageUnavailable();}
   public byte[] readUpload(String k,String c,long s){throw ArtworkException.storageUnavailable();}
   public void putImage(String k,byte[] b){throw ArtworkException.storageUnavailable();}
   public void putSource(String k,byte[] b){throw ArtworkException.storageUnavailable();}
   public String downloadUrl(String k,Duration t){throw ArtworkException.storageUnavailable();}
  };
 }
 @Bean(destroyMethod="close") @ConditionalOnProperty(name="artworkguard.storage.enabled",havingValue="true")
 S3Client s3Client(@Value("${artworkguard.storage.region}") String region,@Value("${artworkguard.storage.endpoint:}") String endpoint) {
  var builder=S3Client.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(!endpoint.isBlank()).build())
   .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(30)).apiCallAttemptTimeout(Duration.ofSeconds(10)).build());
  if(!endpoint.isBlank())builder.endpointOverride(URI.create(endpoint));
  return builder.build();
 }
 @Bean(destroyMethod="close") @ConditionalOnProperty(name="artworkguard.storage.enabled",havingValue="true")
 S3Presigner s3Presigner(@Value("${artworkguard.storage.region}") String region,@Value("${artworkguard.storage.endpoint:}") String endpoint) {
  var builder=S3Presigner.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.builder().build())
   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(!endpoint.isBlank()).build());
  if(!endpoint.isBlank())builder.endpointOverride(URI.create(endpoint));
  return builder.build();
 }
 @Bean @ConditionalOnProperty(name="artworkguard.storage.enabled",havingValue="true")
 ObjectStorage objectStorage(S3Client client,S3Presigner presigner,@Value("${artworkguard.storage.bucket}") String bucket) {
  if(bucket.isBlank())throw new IllegalArgumentException("S3_BUCKET is required when storage is enabled");
  return new S3ObjectStorage(client,presigner,bucket);
 }
}
