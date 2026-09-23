package com.artworkguard.artwork.repository;
import com.artworkguard.artwork.domain.Artwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface ArtworkRepository extends JpaRepository<Artwork,UUID> {
 Optional<Artwork> findByIdAndUserIdAndDeletedFalse(UUID id,UUID userId);
 Page<Artwork> findByUserIdAndDeletedFalse(UUID userId,Pageable pageable);
}
