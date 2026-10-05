package com.antonk404.hhbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Человек, которому разрешён бот. Строка в таблице и есть вайтлист: нет строки - нет доступа.
 *
 * <p>Заводится по username, потому что админ знает только его. {@code telegramId} привязывается
 * при первом сообщении и дальше служит ключом: тег в Telegram можно сменить в любой момент, id - нет.
 */
@Entity
@Table(name = "bot_user")
public class BotUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    /** Чат, куда сканер шлёт карточки и уведомления. Неизвестен, пока человек не открыл бота. */
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "added_by", nullable = false, length = 32)
    private String addedBy;

    /** Общая пауза: правила остаются включёнными, но сканер пользователя пропускает. */
    @Column(nullable = false)
    private boolean paused = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BotUser() {
    }

    public BotUser(String username, String addedBy) {
        this.username = username;
        this.addedBy = addedBy;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
