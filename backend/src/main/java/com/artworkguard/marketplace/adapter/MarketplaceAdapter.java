package com.artworkguard.marketplace.adapter;
import com.artworkguard.marketplace.domain.MarketplaceCode;
import java.util.List;
public interface MarketplaceAdapter {
 MarketplaceCode getMarketplace();
 List<MarketplaceListing> searchProducts(String query,int limit);
 MarketplaceListing getProduct(String externalProductId);
}
