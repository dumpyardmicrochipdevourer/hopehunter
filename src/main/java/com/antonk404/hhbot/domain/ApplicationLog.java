package com.antonk404.hhbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Что бот сделал с вакансией. Одна строка на пару пользователь-вакансия: это и журнал, и
 * долговременная защита от повторного отклика, которая переживает очистку redis.
 */
@Entity
@Table(name = "application_log",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "vacancy_id"}))
public class ApplicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "vacancy_id", nullable = false, length = 32)
    private String vacancyId;

    @Column(name = "vacancy_name", nullable = false, length = 512)
    private String vacancyName;

    @Column(length = 512)
    private String company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApplicationState state;

    @Column(length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ApplicationLog() {
    }

    public ApplicationLog(Long userId, Long ruleId, String vacancyId, String vacancyName,
                          String company, ApplicationState state, String reason) {
        this.userId = userId;
        this.ruleId = ruleId;
        this.vacancyId = vacancyId;
        this.vacancyName = vacancyName;
        this.company = company;
        this.state = state;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getVacancyId() {
        return vacancyId;
    }

    public String getVacancyName() {
        return vacancyName;
    }

    public String getCompany() {
        return company;
    }

    public ApplicationState getState() {
        return state;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
