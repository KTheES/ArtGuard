package com.artworkguard.artwork.dto;
import com.artworkguard.artwork.domain.Artwork;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class ArtworkDtos {
 private ArtworkDtos() {}
 public record UploadRequest(@NotBlank @Pattern(regexp="image/(png|jpeg)") String contentType,
                             @Min(1) @Max(20971520) long sizeBytes) {}
 public record UploadResponse(UUID uploadId,String uploadUrl,Map<String,String> requiredHeaders,Instant expiresAt) {
  @Override public String toString(){return "UploadResponse[REDACTED]";}
 }
 public record CreateRequest(@NotNull UUID uploadId,@NotBlank @Size(max=200) String title,@Size(max=5000) String description) {
  public CreateRequest {title=title==null?null:title.strip();description=description==null?"":description;}
 }
 public record UpdateRequest(@Size(min=1,max=200) String title,@Size(max=5000) String description,Boolean monitoringEnabled,
                             @NotNull @Min(0) Long version) {
  public UpdateRequest {title=title==null?null:title.strip();}
 }
 public record ArtworkResponse(UUID id,String title,String description,int width,int height,boolean monitoringEnabled,
                               Instant createdAt,Instant updatedAt,long version) {
  public static ArtworkResponse from(Artwork a) {
   return new ArtworkResponse(a.getId(),a.getTitle(),a.getDescription(),a.getWidth(),a.getHeight(),a.isMonitoringEnabled(),a.getCreatedAt(),a.getUpdatedAt(),a.getVersion());
  }
 }
 public record ArtworkPage(List<ArtworkResponse> items,int page,int size,long totalElements,int totalPages) {}
 public record ImageUrlResponse(String url,Instant expiresAt) {
  @Override public String toString(){return "ImageUrlResponse[REDACTED]";}
 }
}
