package com.dokpamine.domain.user.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

// FR-15: 동시 닉네임 수정이 다른 요청의 랭킹 참여 해제를 덮어쓰지 않도록 변경한 열만 저장한다.
@Entity
@DynamicUpdate
@Table(name = "users")
public class User {
    @Id private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "ranking_opt_in", nullable = false)
    private boolean rankingOptIn = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(
            UUID id,
            String email,
            String passwordHash,
            String nickname,
            boolean rankingOptIn,
            Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.rankingOptIn = rankingOptIn;
        this.createdAt = createdAt;
    }

    public User(UUID id, String email, String passwordHash, String nickname) {
        this(id, email, passwordHash, nickname, false, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean getRankingOptIn() {
        return rankingOptIn;
    }

    public void setRankingOptIn(boolean rankingOptIn) {
        this.rankingOptIn = rankingOptIn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
