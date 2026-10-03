package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.repo.BotUserRepository;
import com.antonk404.hhbot.repo.HhSessionRepository;
import com.antonk404.hhbot.repo.LetterTemplateRepository;
import com.antonk404.hhbot.repo.ResponseRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class WhitelistService {

    private final BotUserRepository botUserRepository;
    private final HhSessionRepository hhSessionRepository;
    private final ResponseRuleRepository responseRuleRepository;
    private final LetterTemplateRepository letterTemplateRepository;

    public WhitelistService(
            BotUserRepository botUserRepository,
            HhSessionRepository hhSessionRepository,
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository) {
        this.botUserRepository = botUserRepository;
        this.hhSessionRepository = hhSessionRepository;
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
    }

    public BotUser add(String username, String addedBy) {
        String normalized = normalize(username);
        return botUserRepository.findByUsername(normalized)
                .orElseGet(() -> botUserRepository.save(new BotUser(normalized, normalize(addedBy))));
    }

    /**
     * Убирает человека вместе с его сессией hh, правилами и письмами.
     *
     * <p>Сессия - главное: оставить в базе куки того, кого уже не пускают, значит хранить доступ к
     * чужому аккаунту без причины. Правила без владельца сканер продолжал бы обходить. Журнал
     * откликов остаётся - это история, а не доступ.
     */
    @Transactional
    public boolean remove(String username) {
        return botUserRepository.findByUsername(normalize(username))
                .map(user -> {
                    hhSessionRepository.findByUserId(user.getId()).ifPresent(hhSessionRepository::delete);
                    responseRuleRepository.deleteAll(responseRuleRepository.findByUserIdOrderByIdAsc(user.getId()));
                    letterTemplateRepository.deleteAll(letterTemplateRepository.findByUserIdOrderByIdAsc(user.getId()));
                    botUserRepository.delete(user);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Находит отправителя, сначала по id.
     *
     * <p>По id, потому что тег в Telegram меняется в любой момент, и поиск по имени после этого
     * молча перестаёт находить человека. Username остаётся запасным путём для тех, кого добавили
     * в вайтлист, но кто ещё ни разу не открыл бота - их id пока неизвестен.
     */
    public Optional<BotUser> resolve(Long telegramId, String username) {
        if (telegramId != null) {
            Optional<BotUser> byId = botUserRepository.findByTelegramId(telegramId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        return (username == null || username.isBlank())
                ? Optional.empty()
                : botUserRepository.findByUsername(normalize(username));
    }

    public Optional<BotUser> findById(Long id) {
        return botUserRepository.findById(id);
    }

    public List<BotUser> listAll() {
        return botUserRepository.findAllByOrderByUsernameAsc();
    }

    /** Запоминает id и чат: без чата сканеру некуда слать карточки. */
    public void bind(BotUser user, Long telegramId, Long chatId) {
        if (!telegramId.equals(user.getTelegramId()) || !chatId.equals(user.getChatId())) {
            user.setTelegramId(telegramId);
            user.setChatId(chatId);
            botUserRepository.save(user);
        }
    }

    public void setPaused(BotUser user, boolean paused) {
        user.setPaused(paused);
        botUserRepository.save(user);
    }

    public static String normalize(String username) {
        String trimmed = username.trim();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
