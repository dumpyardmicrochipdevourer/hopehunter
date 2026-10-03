package com.antonk404.hhbot.repo;

import com.antonk404.hhbot.domain.HhSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HhSessionRepository extends JpaRepository<HhSession, Long> {

    Optional<HhSession> findByUserId(Long userId);
}
