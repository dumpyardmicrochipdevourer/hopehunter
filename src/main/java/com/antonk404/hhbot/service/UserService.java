package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.repo.BotUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.Optional;

/** Identity is always the signed Telegram ID, never a mutable username. */
@Service
public class UserService {
    private final BotUserRepository users;
    private final boolean publicAccess;
    public UserService(BotUserRepository users, @Value("${bot.public-access:true}") boolean publicAccess) {
        this.users = users;
        this.publicAccess = publicAccess;
    }
    // Synchronize only local registration, not network work. Deployment runs one application instance.
    public synchronized Optional<BotUser> resolve(Long telegramId, String username) {
        if (telegramId == null || telegramId <= 0) return Optional.empty();
        var existing = users.findByTelegramId(telegramId);
        if (existing.isPresent()) {
            var user = existing.get();
            if (!java.util.Objects.equals(user.getTelegramUsername(), username)) {
                user.setTelegramUsername(username);
                users.save(user);
            }
            return Optional.of(user);
        }
        if (!publicAccess) return Optional.empty();
        // Keep the existing unique username column as an internal key for compatibility.
        // ':' cannot occur in a Telegram username, so this never claims an unbound legacy profile.
        var user = new BotUser("tg:" + telegramId, "public");
        user.setTelegramId(telegramId);
        user.setTelegramUsername(username);
        return Optional.of(users.save(user));
    }
    public Optional<BotUser> findById(Long id) { return users.findById(id); }
    public synchronized void bind(BotUser user, Long telegramId, Long chatId) {
        if (!telegramId.equals(user.getTelegramId())) throw new IllegalArgumentException("Telegram identity mismatch");
        if (!chatId.equals(user.getChatId())) {
            user.setChatId(chatId);
            users.save(user);
        }
    }
    public void setPaused(BotUser user, boolean paused) { user.setPaused(paused); users.save(user); }
    public void setNotifyApplied(BotUser user, boolean value) { user.setNotifyApplied(value); users.save(user); }
    public static String normalize(String name) {
        String value = name.trim();
        return (value.startsWith("@") ? value.substring(1) : value).toLowerCase(Locale.ROOT);
    }
}
