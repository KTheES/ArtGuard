package com.artworkguard.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    public enum Role { ROLE_USER, ROLE_CREATOR, ROLE_MODERATOR, ROLE_ADMIN }
    public enum Status { ACTIVE, SUSPENDED, DELETED }
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 50) private String nickname;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected AppUser() {}
    public AppUser(String email, String passwordHash, String nickname) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = Role.ROLE_USER;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }
    public boolean isActive() { return status == Status.ACTIVE; }
}
