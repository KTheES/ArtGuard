package com.artworkguard.detection;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class DetectionReviewDtos {
 private DetectionReviewDtos(){}
 public enum ReviewStatus { NEW,CONFIRMED,DISMISSED;
  @com.fasterxml.jackson.annotation.JsonCreator
  public static ReviewStatus fromJson(com.fasterxml.jackson.databind.JsonNode value){
   if(value==null || !value.isTextual())throw new IllegalArgumentException("Review status must be a string");
   return valueOf(value.textValue());
  }
 }
 public record UpdateRequest(@NotNull ReviewStatus status,@NotNull @Min(0) Long version){}
 public record Item(UUID id,UUID artworkId,String artworkTitle,UUID productId,String productTitle,String productUrl,
  double similarity,DetectionPolicy.Severity severity,ReviewStatus status,long version,
  Instant firstDetectedAt,Instant lastDetectedAt,boolean currentEvidence,boolean synthetic){}
 public record Region(String key,String scheme,double x,double y,double width,double height){}
 public record Scores(double embedding,Double pHash,Double dHash,String ensembleVersion,double finalScore){}
 public record Detail(Item detection,UUID imageId,UUID artworkEmbeddingId,UUID productEmbeddingId,UUID lastRunId,
  String model,String modelVersion,String preprocessingVersion,String imageHash,Instant reviewedAt,Region matchedRegion,Scores scores){}
 public record Page(List<Item> items,int page,int size,long totalElements,long totalPages){}
}
