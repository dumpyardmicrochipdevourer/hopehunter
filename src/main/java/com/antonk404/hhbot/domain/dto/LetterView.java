package com.antonk404.hhbot.domain.dto;

import com.antonk404.hhbot.domain.LetterTemplate;

public record LetterView(Long id, String name, String body) {

    public static LetterView of(LetterTemplate template) {
        return new LetterView(template.getId(), template.getName(), template.getBody());
    }
}
