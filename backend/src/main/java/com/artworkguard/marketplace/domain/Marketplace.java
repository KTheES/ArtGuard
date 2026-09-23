package com.artworkguard.marketplace.domain;
import java.util.UUID;
public record Marketplace(UUID id,MarketplaceCode code,String name,String baseUrl,boolean enabled){}
