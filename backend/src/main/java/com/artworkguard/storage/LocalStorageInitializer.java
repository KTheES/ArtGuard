package com.artworkguard.storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.net.URI;
import java.util.Set;
@Component
@ConditionalOnProperty(name={"artworkguard.storage.enabled","artworkguard.storage.initialize-local"},havingValue="true")
public class LocalStorageInitializer implements ApplicationRunner {
 private final S3Client client;
 private final String bucket;
 private final String endpoint;
 public LocalStorageInitializer(S3Client client,@Value("${artworkguard.storage.bucket}") String bucket,@Value("${artworkguard.storage.endpoint:}") String endpoint){
  this.client=client;this.bucket=bucket;this.endpoint=endpoint;
 }
 @Override public void run(ApplicationArguments arguments) {
  if(endpoint.isBlank() || !Set.of("localhost","127.0.0.1","[::1]").contains(URI.create(endpoint).getHost()))
   throw new IllegalStateException("Local bucket initialization requires a loopback S3_ENDPOINT");
  try {client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());}
  catch(S3Exception e) {
   if(e.statusCode()!=404)throw e;
   client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
   // Only a newly created local bucket receives this lifecycle policy.
   client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder().bucket(bucket)
    .lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(LifecycleRule.builder().id("expire-staged-uploads")
     .status(ExpirationStatus.ENABLED).filter(LifecycleRuleFilter.builder().prefix("uploads/").build())
     .expiration(LifecycleExpiration.builder().days(1).build()).build()).build()).build());
  }
 }
}
