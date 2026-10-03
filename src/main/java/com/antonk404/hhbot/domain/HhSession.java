package com.antonk404.hhbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Сессия hh одного пользователя. Куки лежат только зашифрованными, см. {@code CookieCipher}. */
@Entity
@Table(name = "hh_session")
public class HhSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "cookies_encrypted", nullable = false, length = 16384)
    private String cookiesEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionState state = SessionState.ACTIVE;

    /** Имя из профиля hh - для алиаса {@code [my_name]} в письме. */
    @Column(name = "owner_name", length = 128)
    private String ownerName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected HhSession() {
    }

    public HhSession(Long userId, String cookiesEncrypted, String ownerName) {
        this.userId = userId;
        this.cookiesEncrypted = cookiesEncrypted;
        this.ownerName = ownerName;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCookiesEncrypted() {
        return cookiesEncrypted;
    }

    public SessionState getState() {
        return state;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void renew(String cookiesEncrypted, String ownerName) {
        this.cookiesEncrypted = cookiesEncrypted;
        this.ownerName = ownerName;
        this.state = SessionState.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        this.state = SessionState.EXPIRED;
        this.updatedAt = Instant.now();
    }
}
