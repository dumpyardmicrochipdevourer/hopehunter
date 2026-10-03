package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.hh.HhVacancy;
import com.antonk404.hhbot.store.KeyValueStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Держит вакансию между карточкой и нажатием на её кнопку.
 *
 * <p>В callback data помещается 64 байта - id вакансии влезает, а название, компания и ссылка
 * нет. Кнопка несёт id, остальное лежит здесь. Ключ включает пользователя, поэтому чужую
 * карточку нажатием не достать, даже подделав data.
 */
@Component
public class VacancyCards {

    /** @param ruleId правило, которое нашло вакансию - из него берутся резюме и письмо */
    public record Card(Long ruleId, HhVacancy vacancy) {
    }

    /** Неделя: дольше карточка без ответа - уже не вакансия, а археология. */
    private static final Duration TTL = Duration.ofDays(7);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeyValueStore store;

    public VacancyCards(KeyValueStore store) {
        this.store = store;
    }

    public void stage(Long userId, Long ruleId, HhVacancy vacancy) {
        try {
            store.set(key(userId, vacancy.id()), MAPPER.writeValueAsString(new Card(ruleId, vacancy)), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("vacancy card is not serializable", e);
        }
    }

    public Optional<Card> find(Long userId, String vacancyId) {
        return store.get(key(userId, vacancyId)).map(json -> {
            try {
                return MAPPER.readValue(json, Card.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("stored vacancy card is corrupted", e);
            }
        });
    }

    public void drop(Long userId, String vacancyId) {
        store.delete(key(userId, vacancyId));
    }

    private static String key(Long userId, String vacancyId) {
        return "vac:" + userId + ":" + vacancyId;
    }
}
