package com.antonk404.hhbot.repo;

import com.antonk404.hhbot.domain.BotUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {

    Optional<BotUser> findByUsername(String username);

    Optional<BotUser> findByTelegramId(Long telegramId);

    List<BotUser> findAllByOrderByUsernameAsc();
}
