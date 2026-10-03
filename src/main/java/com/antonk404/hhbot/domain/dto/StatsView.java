package com.antonk404.hhbot.domain.dto;

import java.time.Instant;
import java.util.List;

public record StatsView(long sentToday, long sentTotal, long manual, long skipped, List<Entry> recent) {

    public record Entry(String vacancyId, String vacancyName, String company, Instant at) {
    }
}
