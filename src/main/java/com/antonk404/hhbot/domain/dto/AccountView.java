package com.antonk404.hhbot.domain.dto;

/** @param state NONE, ACTIVE или EXPIRED */
public record AccountView(String state, String ownerName) {
}
