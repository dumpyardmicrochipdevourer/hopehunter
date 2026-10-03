package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.service.util.LetterRenderer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Справочники для форм. С сервера, а не зашитые в приложение: список алиасов и потолок лимита
 * проверяет сервер, и расхождение с формой выглядело бы как «выбрал из списка - получил ошибку».
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    public record Option(String code, String label) {
    }

    public record Area(int id, String name) {
    }

    public record Alias(String name, String description) {
    }

    public record Meta(List<Area> areas, List<Option> experience, List<Alias> aliases,
                       int defaultDailyLimit, int maxDailyLimit) {
    }

    private static final Meta META = new Meta(
            List.of(new Area(1, "Москва"), new Area(2, "Санкт-Петербург"), new Area(113, "Вся Россия")),
            List.of(new Option("noExperience", "без опыта"), new Option("between1And3", "1–3 года"),
                    new Option("between3And6", "3–6 лет"), new Option("moreThan6", "больше 6 лет")),
            List.of(new Alias("company_name", "компания"), new Alias("vacancy_name", "название вакансии"),
                    new Alias("salary", "зарплата из вакансии, если указана"), new Alias("city", "город"),
                    new Alias("my_name", "твоё имя из hh")),
            ResponseRule.DEFAULT_DAILY_LIMIT, ResponseRule.MAX_DAILY_LIMIT);

    static {
        // Справочник и проверки обязаны совпадать; расхождение - ошибка сборки, а не сюрприз в форме.
        if (!META.aliases().stream().map(Alias::name).toList().equals(LetterRenderer.ALIASES)
                || META.experience().size() != ResponseRule.EXPERIENCE_CODES.size()
                || !META.experience().stream().allMatch(option -> ResponseRule.EXPERIENCE_CODES.contains(option.code()))) {
            throw new IllegalStateException("meta is out of sync with validation");
        }
    }

    @GetMapping
    public Meta meta() {
        return META;
    }
}
