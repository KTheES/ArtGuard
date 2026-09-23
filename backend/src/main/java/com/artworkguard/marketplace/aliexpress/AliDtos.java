package com.artworkguard.marketplace.aliexpress;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class AliDtos {
 private AliDtos(){}
 public record SearchRequest(@NotBlank @Size(max=100) String query,@Min(1) @Max(100) int page,@Min(1) @Max(5) int limit){}
 public record Listing(String id,String title,String shopId,BigDecimal price,String currency,String imageUrl){}
 public record Prepared(Listing listing,String key,String hash,int width,int height,int size){}
}
