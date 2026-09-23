package com.artworkguard.marketplace.service;
import com.artworkguard.auth.service.AuthException;
import com.artworkguard.user.domain.AppUser;
import com.artworkguard.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class CatalogAccess {
 private final UserRepository users;
 public CatalogAccess(UserRepository users){this.users=users;}
 public AppUser active(UUID id){return users.findById(id).filter(AppUser::isActive).orElseThrow(AuthException::unauthorized);}
 public void admin(UUID id){
  if(active(id).getRole()!=AppUser.Role.ROLE_ADMIN)throw new CatalogException(HttpStatus.FORBIDDEN,"ADMIN_REQUIRED","관리자 권한이 필요합니다.");
 }
}
