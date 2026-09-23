package com.artworkguard.product.embedding;
import com.artworkguard.common.response.ApiResponse;
import com.artworkguard.marketplace.service.*;
import com.artworkguard.product.repository.CatalogRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/products/{productId}/images/{imageId}/embedding")
public class ProductEmbeddingController {
 private final CatalogAccess access;private final CatalogRepository catalog;private final ProductEmbeddingJobService jobs;
 public ProductEmbeddingController(CatalogAccess access,CatalogRepository catalog,ProductEmbeddingJobService jobs){this.access=access;this.catalog=catalog;this.jobs=jobs;}
 @GetMapping public ApiResponse<ProductEmbeddingJobService.JobResponse> status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId,@PathVariable UUID imageId){
  access.active(UUID.fromString(jwt.getSubject()));requireImage(productId,imageId);return ApiResponse.ok(jobs.status(imageId));
 }
 @PostMapping public ApiResponse<ProductEmbeddingJobService.JobResponse> enqueue(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId,@PathVariable UUID imageId){
  access.admin(UUID.fromString(jwt.getSubject()));requireImage(productId,imageId);return ApiResponse.ok(jobs.enqueue(imageId));
 }
 private void requireImage(UUID product,UUID image){catalog.image(product,image).orElseThrow(CatalogException::notFound);}
}
