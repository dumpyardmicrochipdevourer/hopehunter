package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.AccountView;
import com.antonk404.hhbot.domain.dto.MeView;
import com.antonk404.hhbot.domain.dto.StatsView;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** Сводки для главного экрана и статистики мини-приложения. */
@Service
public class DashboardService {

    private final HhAccountService hhAccountService;
    private final ResponseRuleRepository responseRuleRepository;
    private final ApplicationLogRepository applicationLogRepository;
    private final RateLimiter rateLimiter;
    private final ZoneId zone;

    public DashboardService(
            HhAccountService hhAccountService,
            ResponseRuleRepository responseRuleRepository,
            ApplicationLogRepository applicationLogRepository,
            RateLimiter rateLimiter,
            @Value("${bot.timezone}") String timezone) {
        this.hhAccountService = hhAccountService;
        this.responseRuleRepository = responseRuleRepository;
        this.applicationLogRepository = applicationLogRepository;
        this.rateLimiter = rateLimiter;
        this.zone = ZoneId.of(timezone);
    }

    public MeView me(BotUser user) {
        List<ResponseRule> rules = responseRuleRepository.findByUserIdOrderByIdAsc(user.getId());
        int enabled = (int) rules.stream().filter(ResponseRule::isEnabled).count();
        return new MeView(user.getUsername(), user.isPaused(), rateLimiter.isStopped(user.getId()),
                account(user), rules.size(), enabled, sentToday(user));
    }

    public AccountView account(BotUser user) {
        Optional<HhSession> session = hhAccountService.session(user.getId());
        return session
                .map(found -> new AccountView(found.getState().name(), found.getOwnerName()))
                .orElseGet(() -> new AccountView("NONE", null));
    }

    public StatsView stats(BotUser user) {
        Long id = user.getId();
        List<StatsView.Entry> recent = applicationLogRepository
                .findTop10ByUserIdAndStateOrderByCreatedAtDesc(id, ApplicationState.SENT).stream()
                .map(log -> new StatsView.Entry(log.getVacancyId(), log.getVacancyName(), log.getCompany(), log.getCreatedAt()))
                .toList();
        return new StatsView(
                sentToday(user),
                applicationLogRepository.countByUserIdAndState(id, ApplicationState.SENT),
                applicationLogRepository.countByUserIdAndState(id, ApplicationState.MANUAL),
                applicationLogRepository.countByUserIdAndState(id, ApplicationState.SKIPPED),
                recent);
    }

    private long sentToday(BotUser user) {
        Instant midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return applicationLogRepository.countByUserIdAndStateAndCreatedAtAfter(
                user.getId(), ApplicationState.SENT, midnight);
    }
}
