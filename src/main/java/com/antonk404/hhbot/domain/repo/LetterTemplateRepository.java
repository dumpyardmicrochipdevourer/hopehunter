package com.antonk404.hhbot.domain.repo;

import com.antonk404.hhbot.domain.LetterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LetterTemplateRepository extends JpaRepository<LetterTemplate, Long> {

    List<LetterTemplate> findByUserIdOrderByIdAsc(Long userId);

    Optional<LetterTemplate> findByIdAndUserId(Long id, Long userId);
}
