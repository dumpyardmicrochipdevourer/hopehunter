package com.antonk404.hhbot.domain.repo;

import com.antonk404.hhbot.domain.ApplicationLog;
import com.antonk404.hhbot.domain.ApplicationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ApplicationLogRepository extends JpaRepository<ApplicationLog, Long> {

    boolean existsByUserIdAndVacancyId(Long userId, String vacancyId);

    long countByUserIdAndState(Long userId, ApplicationState state);

    long countByUserIdAndStateAndCreatedAtAfter(Long userId, ApplicationState state, Instant after);

    List<ApplicationLog> findTop10ByUserIdAndStateOrderByCreatedAtDesc(Long userId, ApplicationState state);
}
