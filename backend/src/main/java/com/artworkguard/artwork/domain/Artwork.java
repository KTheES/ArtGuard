package com.artworkguard.artwork.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="artwork")
public class Artwork {
 @Id private UUID id;
 @Column(nullable=false) private UUID userId;
 @Column(nullable=false,length=200) private String title;
 @Column(nullable=false,length=5000) private String description;
 @Column(nullable=false,length=300) private String originalKey;
 @Column(nullable=false,length=300) private String thumbnailKey;
 private int width;
 private int height;
 private boolean monitoringEnabled;
 private boolean deleted;
 @Column(nullable=false) private Instant createdAt;
 @Column(nullable=false) private Instant updatedAt;
 @Version private long version;
 protected Artwork() {}
 public Artwork(UUID id,UUID userId,String title,String description,int width,int height,Instant now) {
  this.id=id;this.userId=userId;this.title=title;this.description=description;
  this.width=width;this.height=height;this.createdAt=now;this.updatedAt=now;
  this.originalKey="artworks/"+userId+"/"+id+"/original.png";
  this.thumbnailKey="artworks/"+userId+"/"+id+"/thumbnail.png";
 }
 public void update(String title,String description,Boolean monitoring,Instant now) {
  if(title!=null)this.title=title;
  if(description!=null)this.description=description;
  if(monitoring!=null)this.monitoringEnabled=monitoring;
  this.updatedAt=now;
 }
 public void delete(Instant now){this.deleted=true;this.monitoringEnabled=false;this.updatedAt=now;}
 public UUID getId(){return id;} public UUID getUserId(){return userId;}
 public String getTitle(){return title;} public String getDescription(){return description;}
 public String getOriginalKey(){return originalKey;} public String getThumbnailKey(){return thumbnailKey;}
 public int getWidth(){return width;} public int getHeight(){return height;}
 public boolean isMonitoringEnabled(){return monitoringEnabled;} public boolean isDeleted(){return deleted;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public long getVersion(){return version;}
}
