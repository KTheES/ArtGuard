package com.artworkguard.artwork.repository;
import com.artworkguard.artwork.domain.ArtworkUpload;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ArtworkUploadRepository extends JpaRepository<ArtworkUpload,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select u from ArtworkUpload u where u.id=:id and u.userId=:userId")
 Optional<ArtworkUpload> findOwnedForUpdate(@Param("id") UUID id,@Param("userId") UUID userId);
}
