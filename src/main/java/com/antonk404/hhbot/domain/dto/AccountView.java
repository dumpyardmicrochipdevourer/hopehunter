package com.antonk404.hhbot.domain.dto;

import java.time.Instant;

/**
 * @param state     NONE, ACTIVE или EXPIRED
 * @param updatedAt когда куки последний раз подключали или гасили; null, если аккаунта нет
 */
public record AccountView(String state, String ownerName, Instant updatedAt) {
}
