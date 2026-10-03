package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.dto.HhVacancy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VacancyPresenterTest {

    @Test
    void describesVacancyWithoutEmptyLines() {
        assertEquals("Java\nAcme · Москва\nот 200 000 ₽", VacancyPresenter.describe(
                new HhVacancy("1", "Java", "Acme", "от 200 000 ₽", "Москва", "u", false, false)));
        assertEquals("Java\nAcme", VacancyPresenter.describe(
                new HhVacancy("1", "Java", "Acme", "", "", "u", false, false)));
    }
}
