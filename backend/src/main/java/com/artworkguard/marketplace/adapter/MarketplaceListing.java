package com.artworkguard.marketplace.adapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
public record MarketplaceListing(
 @NotBlank @Size(max=200) String externalProductId,
 @NotBlank @Size(max=500) String title,
 @NotBlank @Size(max=2048) @Pattern(regexp="https://mock\\.artworkguard\\.invalid/products/[a-z0-9-]+") String productUrl,
 @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal price,
 @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
 @NotNull @Valid SellerListing seller,
 @NotEmpty @Size(max=10) List<@Valid ImageListing> images) {
 public MarketplaceListing { if(images!=null)images=List.copyOf(images); }
 public record SellerListing(
  @NotBlank @Size(max=200) String externalSellerId,
  @NotBlank @Size(max=200) String name,
  @NotBlank @Size(max=2048) @Pattern(regexp="https://mock\\.artworkguard\\.invalid/sellers/[a-z0-9-]+") String url){}
 public record ImageListing(
  @NotBlank @Size(max=200) String externalImageId,
  @NotBlank @Size(max=2048) @Pattern(regexp="https://mock\\.artworkguard\\.invalid/images/[a-z0-9-]+\\.png") String originalUrl,
  @NotBlank @Pattern(regexp="mock-marketplace/images/[a-z0-9-]+\\.png") String sourceKey,
  @Min(1) @Max(8192) int width,@Min(1) @Max(8192) int height,
  @NotBlank @Pattern(regexp="[a-f0-9]{64}") String imageHash){}
}
