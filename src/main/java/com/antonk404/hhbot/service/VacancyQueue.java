package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.store.KeyValueStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** One persisted page per rule: bounded memory, no lost work on restart. */
@Component
public class VacancyQueue {
    public record Page(String fingerprint, int nextPage, List<HhVacancy> pending, long retryAt) {
        public Page { pending = new ArrayList<>(pending); }
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final KeyValueStore store;
    public VacancyQueue(KeyValueStore store) { this.store = store; }
    public Page load(Long ruleId, String fingerprint) {
        return store.get("queue:" + ruleId).map(value -> {
            try { return JSON.readValue(value, Page.class); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Invalid vacancy queue", e); }
        }).filter(page -> page.fingerprint().equals(fingerprint))
                .orElseGet(() -> new Page(fingerprint, 0, List.of(), 0));
    }
    public void save(Long ruleId, Page page) {
        try { store.set("queue:" + ruleId, JSON.writeValueAsString(page), Duration.ofDays(30)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot save vacancy queue", e); }
    }
}
