package com.antonk404.hhbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Шаблон сопроводительного письма с алиасами вида {@code [company_name]}. */
@Entity
@Table(name = "letter_template")
public class LetterTemplate {

    /** Немного ниже потолка hh на длину письма, чтобы подстановка алиасов его не пробила. */
    public static final int MAX_BODY = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = MAX_BODY)
    private String body;

    protected LetterTemplate() {
    }

    public LetterTemplate(Long userId, String name, String body) {
        this.userId = userId;
        this.name = name;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
