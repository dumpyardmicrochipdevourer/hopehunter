package com.antonk404.hhbot.telegram.state;

import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.store.InMemoryKeyValueStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VacancyCardsTest {

    @Test
    void cardRoundTripsAndIsScopedToItsUser() {
        VacancyCards cards = new VacancyCards(new InMemoryKeyValueStore());
        HhVacancy vacancy = new HhVacancy("42", "Java", "Acme", "", "Москва", "https://hh.ru/vacancy/42", true, false);

        cards.stage(1L, 7L, vacancy);

        assertEquals(Optional.of(new VacancyCards.Card(7L, vacancy)), cards.find(1L, "42"));
        assertEquals(Optional.empty(), cards.find(2L, "42"));
        cards.drop(1L, "42");
        assertEquals(Optional.empty(), cards.find(1L, "42"));
    }
}
