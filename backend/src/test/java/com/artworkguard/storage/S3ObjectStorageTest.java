package com.artworkguard.storage;
import com.artworkguard.artwork.service.ArtworkException;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.regions.Region;
import java.net.URI;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class S3ObjectStorageTest {
 S3Client client=mock(S3Client.class);
 S3Presigner presigner;
 S3ObjectStorage storage;
 @BeforeEach void setup() {
  presigner=S3Presigner.builder().region(Region.US_EAST_1).endpointOverride(URI.create("http://localhost:9000"))
   .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
   .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-only-key","test-only-secret"))).build();
  storage=new S3ObjectStorage(client,presigner,"private-artworks");
 }
 @AfterEach void close(){presigner.close();}
 @Test void sourceFilePreservesBytesAndUsesBinaryContentType()throws Exception{
  byte[] bytes={0,1,2,3};storage.putSource("ownership-source/test",bytes);
  var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
  var body=org.mockito.ArgumentCaptor.forClass(software.amazon.awssdk.core.sync.RequestBody.class);
  verify(client).putObject(request.capture(),body.capture());
  assertThat(request.getValue().contentType()).isEqualTo("application/octet-stream");
  try(var stream=body.getValue().contentStreamProvider().newStream()){assertThat(stream.readAllBytes()).isEqualTo(bytes);}
 }
 @Test void uploadSignatureBindsToPrivateObjectAndExpiresInTenMinutes() {
  var signed=storage.uploadUrl("uploads/owner/ticket","image/png",42,Duration.ofMinutes(10));
  assertThat(signed.url()).contains("/private-artworks/uploads/owner/ticket","X-Amz-Expires=600","X-Amz-Signature=");
  assertThat(signed.headers()).containsEntry("content-type","image/png").containsEntry("content-length","42");
  assertThat(signed.url()).doesNotContain("public-read");
 }
 @Test void readUrlIsShortLivedAndNoStore() {
  assertThat(storage.downloadUrl("artworks/owner/id/original.png",Duration.ofSeconds(60)))
   .contains("X-Amz-Expires=60","response-cache-control=");
 }
 @Test void rejectsSizeMismatchWithoutDownloading() {
  when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(999L).contentType("image/png").eTag("etag").build());
  assertThatThrownBy(()->storage.readUpload("uploads/id","image/png",10)).isInstanceOf(ArtworkException.class);
  verify(client,never()).getObject(any(GetObjectRequest.class));
 }
 @Test void missingUploadBecomesSafeValidationError() {
  when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).message("internal bucket details").build());
  assertThatThrownBy(()->storage.readUpload("uploads/id","image/png",10)).isInstanceOf(ArtworkException.class).hasMessageNotContaining("internal");
 }
}
