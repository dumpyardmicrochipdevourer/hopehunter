package com.antonk404.hhbot.repo;

import com.antonk404.hhbot.domain.ResponseRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResponseRuleRepository extends JpaRepository<ResponseRule, Long> {

    List<ResponseRule> findByUserIdOrderByIdAsc(Long userId);

    /** С владельцем в условии: id правила приходит из callback data, то есть от клиента. */
    Optional<ResponseRule> findByIdAndUserId(Long id, Long userId);

    List<ResponseRule> findByEnabledTrueOrderByIdAsc();

    List<ResponseRule> findByLetterTemplateId(Long letterTemplateId);
}
