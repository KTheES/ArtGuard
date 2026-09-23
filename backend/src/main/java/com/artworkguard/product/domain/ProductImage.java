package com.artworkguard.product.domain;
import java.util.UUID;
public record ProductImage(UUID id,UUID productId,String externalImageId,String originalUrl,String sourceType,String sourceKey,int width,int height,String imageHash,int sizeBytes){}
