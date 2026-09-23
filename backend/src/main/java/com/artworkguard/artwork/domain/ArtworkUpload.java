package com.artworkguard.artwork.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="artwork_upload")
public class ArtworkUpload {
 @Id private UUID id;
 @Column(nullable=false) private UUID userId;
 @Column(nullable=false,length=300) private String objectKey;
 @Column(nullable=false,length=50) private String contentType;
 private long sizeBytes;
 @Column(nullable=false) private Instant expiresAt;
 private UUID artworkId;
 protected ArtworkUpload() {}
 public ArtworkUpload(UUID userId,String contentType,long sizeBytes,Instant expiresAt) {
  this.id=UUID.randomUUID();this.userId=userId;this.contentType=contentType;
  this.sizeBytes=sizeBytes;this.expiresAt=expiresAt;this.objectKey="uploads/"+userId+"/"+id;
 }
 public void consume(UUID artworkId){this.artworkId=artworkId;}
 public UUID getId(){return id;} public UUID getUserId(){return userId;}
 public String getObjectKey(){return objectKey;} public String getContentType(){return contentType;}
 public long getSizeBytes(){return sizeBytes;} public Instant getExpiresAt(){return expiresAt;}
 public UUID getArtworkId(){return artworkId;}
}
