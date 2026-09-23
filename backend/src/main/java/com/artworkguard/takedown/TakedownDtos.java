package com.artworkguard.takedown;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public final class TakedownDtos {
 private TakedownDtos(){}
 public enum Status { DRAFT, SUBMITTED, RESOLVED, REJECTED, WITHDRAWN;
  @com.fasterxml.jackson.annotation.JsonCreator
  public static Status fromJson(JsonNode value){
   if(value==null || !value.isTextual())throw new IllegalArgumentException("Status must be text");
   return valueOf(value.textValue());
  }
 }
 public record Create(@NotNull UUID evidenceId,@NotNull @Min(0) Long detectionVersion,
                      @NotNull @AssertTrue Boolean rightsConfirmed){}
 public record Update(@NotNull Status status,@NotNull @Min(0) Long version,
                      @Size(max=200) String externalReference){}
 public record Report(UUID id,UUID detectionId,UUID evidenceId,Status status,long version,
                      JsonNode draft,String externalReference,Instant createdAt,Instant updatedAt){}
}
