package com.artworkguard.social;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class SocialDtos {
 private SocialDtos(){}
 public enum State { PENDING,VERIFIED,REJECTED,REVOKED,EXPIRED }
 public record Start(@NotBlank @Size(max=2048) String profileUrl){}
 public record Review(@NotNull @Min(0) Long version,@NotNull Boolean approved,
  @Size(max=64) String observedCode,@NotNull @AssertTrue Boolean profileChecked,
  @NotBlank @Size(max=2000) String reason){}
 public record Revoke(@NotNull @Min(0) Long version){}
 public record Check(UUID id,UUID ownerId,String profileUrl,String challenge,State state,long version,
  Instant createdAt,Instant expiresAt,Instant verifiedUntil,UUID reviewerId,String reason){
  public Check withoutChallenge(){return new Check(id,ownerId,profileUrl,null,state,version,createdAt,expiresAt,verifiedUntil,reviewerId,reason);}
 }
 public record Page(List<Check> items,int page,int size,long totalElements,long totalPages){}
}
