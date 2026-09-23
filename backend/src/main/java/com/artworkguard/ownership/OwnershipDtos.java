package com.artworkguard.ownership;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
public final class OwnershipDtos {
 private OwnershipDtos(){}
 public enum Status { PENDING,ACCEPTED,REJECTED;
  @com.fasterxml.jackson.annotation.JsonCreator public static Status from(JsonNode value){
   if(value==null || !value.isTextual())throw new IllegalArgumentException("Status must be text");return valueOf(value.textValue());
  }
 }
 public record Submit(@NotBlank @Size(max=2048) String publicationUrl,@NotBlank @Size(max=4000) String statement,
                      @NotNull @AssertTrue Boolean authorized){}
 public record Review(@NotNull Status status,@NotBlank @Size(max=2000) String reason,@NotNull @Min(0) Long version){}
 public record Claim(UUID id,UUID artworkId,UUID ownerId,String publicationUrl,String statement,Status status,
                     String reviewReason,UUID reviewerId,long version,Instant submittedAt,Instant reviewedAt){}
 public record Page(List<Claim> items,int page,int size,long totalElements,long totalPages){}
}
