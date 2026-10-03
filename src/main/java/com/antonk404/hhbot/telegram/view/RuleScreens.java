package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.util.RateLimiter;
import com.antonk404.hhbot.service.util.RuleMatcher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.antonk404.hhbot.telegram.view.Kb.btn;
import static com.antonk404.hhbot.telegram.view.Kb.field;

/**
 * Раздел «Правила».
 *
 * <p>Настройки правила разложены на три группы - что искать, чем откликаться, как откликаться.
 * Все шестнадцать кнопок на одном экране читались как пульт, в котором непонятно, что обязательно,
 * а что можно не трогать.
 */
@Component
public class RuleScreens {

    /** Группы настроек: действие в callback data, оно же - куда вернуться после правки. */
    public static final String SEARCH = "gs";
    public static final String REPLY = "gr";
    public static final String HOW = "gh";

    /** Регионы, которые нужны почти всем. Остальные вводятся числом - id из адреса поиска hh. */
    public static final Map<Integer, String> AREAS = new LinkedHashMap<>();

    public static final Map<String, String> EXPERIENCE = new LinkedHashMap<>();

    /** Готовые пороги зарплаты: набрать «200000» с телефона дольше, чем нажать кнопку. */
    public static final List<Integer> SALARIES = List.of(100_000, 150_000, 200_000, 250_000, 300_000, 400_000);

    public static final List<Integer> LIMITS = List.of(5, 10, 20, 30, 50);

    static {
        AREAS.put(1, "Москва");
        AREAS.put(2, "Санкт-Петербург");
        AREAS.put(113, "Вся Россия");
        EXPERIENCE.put("noExperience", "без опыта");
        EXPERIENCE.put("between1And3", "1–3 года");
        EXPERIENCE.put("between3And6", "3–6 лет");
        EXPERIENCE.put("moreThan6", "больше 6 лет");
    }

    private final ResponseRuleRepository responseRuleRepository;
    private final LetterTemplateRepository letterTemplateRepository;
    private final RateLimiter rateLimiter;

    public RuleScreens(
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository,
            RateLimiter rateLimiter) {
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
        this.rateLimiter = rateLimiter;
    }

    public Screen list(BotUser user) {
        List<ResponseRule> rules = responseRuleRepository.findByUserIdOrderByIdAsc(user.getId());
        Kb keyboard = Kb.of();
        for (ResponseRule rule : rules) {
            keyboard.row(btn((rule.isEnabled() ? "🟢 " : "⚪️ ") + rule.getName(), Cb.rule(rule.getId(), "show")));
        }
        String text = rules.isEmpty()
                ? "<b>Правила</b>\n\nПока ни одного."
                : "<b>Правила</b>\n\n🟢 включено · ⚪️ выключено";
        return new Screen(text, keyboard
                .row(btn("➕ Новое правило", Cb.RULE_NEW))
                .row(btn("🏠 Меню", Cb.HOME))
                .build());
    }

    /** Сводка правила: всё значимое читается с одного экрана, правки - внутри групп. */
    public Screen rule(ResponseRule rule) {
        Long id = rule.getId();
        StringBuilder text = new StringBuilder();
        text.append(rule.isEnabled() ? "🟢 " : "⚪️ ").append(Html.b(rule.getName())).append("\n\n");

        text.append("🔎 ").append(blank(rule.getKeywords()) ? "<i>ключевые слова не заданы</i>" : Html.code(rule.getKeywords()));
        text.append('\n').append(Html.esc(filters(rule))).append("\n\n");

        text.append("📄 ").append(rule.getResumeTitle() == null ? "<i>резюме не выбрано</i>" : Html.esc(rule.getResumeTitle()));
        text.append('\n').append("✉️ ").append(Html.esc(letterName(rule))).append("\n\n");

        text.append(rule.getMode() == RuleMode.AUTO ? "⚡️ Откликаюсь сам" : "👀 Спрашиваю перед откликом");
        text.append(" · сегодня ").append(rateLimiter.sentToday(rule)).append(" из ").append(rule.getDailyLimit());
        if (!rule.isReady()) {
            text.append("\n\n⚠️ Чтобы включить, нужны ключевые слова и резюме.");
        }

        return new Screen(text.toString(), Kb.of()
                .row(btn("🔎 Что искать", Cb.rule(id, SEARCH)), btn("📄 Чем откликаться", Cb.rule(id, REPLY)))
                .row(btn("⚙️ Как откликаться", Cb.rule(id, HOW)))
                .row(btn("🔍 Проверить сейчас", Cb.rule(id, "test")))
                .row(btn(rule.isEnabled() ? "⚪️ Выключить" : "🟢 Включить", Cb.rule(id, "toggle")))
                .row(btn("✏️ Переименовать", Cb.rule(id, "name")), btn("🗑 Удалить", Cb.rule(id, "del")))
                .row(btn("« К правилам", Cb.RULES))
                .build());
    }

    /** Одна строка с фильтрами, которые человек действительно задал; пустые не перечисляются. */
    private static String filters(ResponseRule rule) {
        StringBuilder out = new StringBuilder(areaName(rule));
        if (rule.getSalaryFrom() != null) {
            out.append(" · от ").append(money(rule.getSalaryFrom())).append(" ₽");
        }
        if (rule.getExperience() != null) {
            out.append(" · опыт ").append(EXPERIENCE.get(rule.getExperience()));
        }
        if (rule.isRemoteOnly()) {
            out.append(" · удалёнка");
        }
        int minus = RuleMatcher.list(rule.getMinusWords()).size();
        if (minus > 0) {
            out.append(" · минус-слов: ").append(minus);
        }
        int blocked = RuleMatcher.list(rule.getCompanyBlacklist()).size();
        if (blocked > 0) {
            out.append(" · компаний в стоп-листе: ").append(blocked);
        }
        return out.toString();
    }

    public Screen search(ResponseRule rule) {
        Long id = rule.getId();
        String text = "<b>Что искать</b> · " + Html.esc(rule.getName()) + "\n\n"
                + "Слова: " + (blank(rule.getKeywords()) ? "<i>не заданы</i>" : Html.code(rule.getKeywords())) + "\n"
                + "Минус-слова: " + orDash(rule.getMinusWords()) + "\n"
                + "Стоп-лист компаний: " + orDash(rule.getCompanyBlacklist());
        return new Screen(text, Kb.of()
                .row(btn("✏️ Ключевые слова", Cb.rule(id, "kw")))
                .row(field("Искать", rule.isTitleOnly() ? "в названии" : "везде", Cb.rule(id, "title")))
                .row(field("Регион", areaName(rule), Cb.rule(id, "area")),
                        field("Удалёнка", rule.isRemoteOnly() ? "да" : "не важно", Cb.rule(id, "remote")))
                .row(field("Зарплата", rule.getSalaryFrom() == null ? "любая" : "от " + money(rule.getSalaryFrom()),
                        Cb.rule(id, "sal")))
                .row(field("Опыт", rule.getExperience() == null ? "любой" : EXPERIENCE.get(rule.getExperience()),
                        Cb.rule(id, "exp")))
                .row(btn("➖ Минус-слова", Cb.rule(id, "minus")), btn("🚫 Стоп-лист", Cb.rule(id, "bl")))
                .row(back(id))
                .build());
    }

    public Screen reply(ResponseRule rule) {
        Long id = rule.getId();
        String text = "<b>Чем откликаться</b> · " + Html.esc(rule.getName()) + "\n\n"
                + "Резюме: " + (rule.getResumeTitle() == null ? "<i>не выбрано</i>" : Html.esc(rule.getResumeTitle())) + "\n"
                + "Письмо: " + Html.esc(letterName(rule));
        return new Screen(text, Kb.of()
                .row(btn("📄 Выбрать резюме", Cb.rule(id, "resume")))
                .row(btn("✉️ Выбрать письмо", Cb.rule(id, "letter")))
                .row(back(id))
                .build());
    }

    public Screen how(ResponseRule rule) {
        Long id = rule.getId();
        boolean auto = rule.getMode() == RuleMode.AUTO;
        String text = "<b>Как откликаться</b> · " + Html.esc(rule.getName()) + "\n\n"
                + (auto
                ? "⚡️ <b>Сам</b>"
                : "👀 <b>С подтверждением</b>")
                + "\n\nЛимит: " + rule.getDailyLimit() + " в день";
        return new Screen(text, Kb.of()
                .row(btn(auto ? "Переключить: спрашивать меня" : "Переключить: откликаться самому", Cb.rule(id, "mode")))
                .row(field("Лимит в день", String.valueOf(rule.getDailyLimit()), Cb.rule(id, "lim")))
                .row(back(id))
                .build());
    }

    public Screen area(ResponseRule rule) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        AREAS.forEach((areaId, name) -> keyboard.row(btn(name, Cb.rule(id, "area:" + areaId))));
        return new Screen("<b>Регион поиска</b>\n\nСейчас: " + Html.esc(areaName(rule)) + ".", keyboard
                .row(btn("Не важно", Cb.rule(id, "area:0")), btn("Другой город…", Cb.rule(id, "areaid")))
                .row(btn("« Назад", Cb.rule(id, SEARCH)))
                .build());
    }

    public Screen experience(ResponseRule rule) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        EXPERIENCE.forEach((code, name) -> keyboard.row(btn(name, Cb.rule(id, "exp:" + code))));
        return new Screen("<b>Опыт в вакансии</b>", keyboard
                .row(btn("Не важно", Cb.rule(id, "exp:any")))
                .row(btn("« Назад", Cb.rule(id, SEARCH)))
                .build());
    }

    public Screen salary(ResponseRule rule) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        for (int i = 0; i < SALARIES.size(); i += 3) {
            keyboard.row(SALARIES.subList(i, i + 3).stream()
                    .map(amount -> btn("от " + amount / 1000 + " тыс.", Cb.rule(id, "sal:" + amount)))
                    .toArray(InlineKeyboardButton[]::new));
        }
        return new Screen("<b>Зарплата от</b>", keyboard
                .row(btn("Не важно", Cb.rule(id, "sal:0")), btn("Своя сумма…", Cb.rule(id, "salx")))
                .row(btn("« Назад", Cb.rule(id, SEARCH)))
                .build());
    }

    public Screen limit(ResponseRule rule) {
        Long id = rule.getId();
        return new Screen("<b>Лимит откликов в день</b>\n\nСейчас: " + rule.getDailyLimit() + ".", Kb.of()
                .row(LIMITS.stream()
                        .map(limit -> btn(String.valueOf(limit), Cb.rule(id, "lim:" + limit)))
                        .toArray(InlineKeyboardButton[]::new))
                .row(btn("Своё число…", Cb.rule(id, "limx")))
                .row(btn("« Назад", Cb.rule(id, HOW)))
                .build());
    }

    /** @param wizard выбор идёт в мастере создания - после него следующий шаг, а не возврат в настройки */
    public Screen resumes(ResponseRule rule, List<HhResume> resumes, boolean wizard) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        resumes.forEach(resume -> keyboard.row(btn(resume.title(), Cb.rule(id, (wizard ? "wrs:" : "rs:") + resume.hash()))));
        String title = wizard ? "<b>Новое правило · шаг 3 из 4</b>\n\n" : "<b>Резюме</b>\n\n";
        String text = resumes.isEmpty()
                ? "На hh нет ни одного резюме. Создай его на сайте и вернись сюда."
                : "Каким резюме откликаться?";
        return new Screen(title + text, keyboard
                .row(wizard ? btn("Пропустить", Cb.rule(id, "wskip")) : btn("« Назад", Cb.rule(id, REPLY)))
                .build());
    }

    public Screen letters(BotUser user, ResponseRule rule) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        List<LetterTemplate> templates = letterTemplateRepository.findByUserIdOrderByIdAsc(user.getId());
        templates.forEach(template -> keyboard.row(btn(template.getName(), Cb.rule(id, "lt:" + template.getId()))));
        String text = templates.isEmpty()
                ? "<b>Письмо</b>\n\nШаблонов нет."
                : "<b>Письмо</b>";
        if (templates.isEmpty()) {
            keyboard.row(btn("➕ Создать письмо", Cb.LETTER_NEW));
        }
        return new Screen(text, keyboard
                .row(btn("Без письма", Cb.rule(id, "lt:0")))
                .row(btn("« Назад", Cb.rule(id, REPLY)))
                .build());
    }

    public Screen wizardMode(ResponseRule rule) {
        Long id = rule.getId();
        return new Screen("<b>Новое правило · шаг 4 из 4</b>\n\nКак откликаться?", Kb.of()
                .row(btn("👀 Спрашивать меня", Cb.rule(id, "wm:confirm")))
                .row(btn("⚡️ Откликаться самому", Cb.rule(id, "wm:auto")))
                .build());
    }

    /** Конец мастера: правило собрано, осталось посмотреть выдачу и включить. */
    public Screen wizardDone(ResponseRule rule) {
        Long id = rule.getId();
        String text = "✅ <b>Правило «" + Html.esc(rule.getName()) + "» готово</b>, выключено.\n\n"
                + (rule.isReady()
                ? ""
                : "⚠️ Резюме не выбрано.");
        return new Screen(text, Kb.of()
                .row(btn("🔍 Посмотреть, что найдёт", Cb.rule(id, "test")))
                .row(btn("🟢 Включить", Cb.rule(id, "toggle")))
                .row(btn("⚙️ Настроить подробнее", Cb.rule(id, "show")))
                .build());
    }

    public Screen delete(ResponseRule rule) {
        Long id = rule.getId();
        return new Screen("Удалить правило «" + Html.esc(rule.getName()) + "»?", Kb.of()
                .row(btn("🗑 Да, удалить", Cb.rule(id, "delok")), btn("Нет", Cb.rule(id, "show")))
                .build());
    }

    /** Результат «Проверить сейчас»: что правило нашло бы, без единого отклика. */
    public Screen preview(ResponseRule rule, List<HhVacancy> vacancies, int shown) {
        StringBuilder text = new StringBuilder("<b>Проверка · ").append(Html.esc(rule.getName())).append("</b>\n");
        text.append("Ничего не отправлено.\n");
        if (vacancies.isEmpty()) {
            text.append("\nНичего не подошло.");
        } else {
            text.append("Подходит: <b>").append(vacancies.size()).append("</b>\n");
        }
        vacancies.stream().limit(shown).forEach(vacancy -> text.append('\n').append(VacancyView.text(vacancy))
                .append(vacancy.hasTest() ? "\n<i>с тестом - пришлю ссылкой</i>" : "").append('\n'));
        return new Screen(text.toString(), Kb.of()
                .row(btn("🔎 Изменить поиск", Cb.rule(rule.getId(), SEARCH)))
                .row(back(rule.getId()))
                .build());
    }

    private static InlineKeyboardButton back(Long id) {
        return btn("« К правилу", Cb.rule(id, "show"));
    }

    private String letterName(ResponseRule rule) {
        if (rule.getLetterTemplateId() == null) {
            return "без письма";
        }
        return letterTemplateRepository.findById(rule.getLetterTemplateId())
                .map(LetterTemplate::getName)
                .orElse("без письма");
    }

    static String areaName(ResponseRule rule) {
        if (rule.getAreaId() == null) {
            return "любой регион";
        }
        if (rule.getAreaName() != null) {
            return rule.getAreaName();
        }
        return AREAS.getOrDefault(rule.getAreaId(), "регион " + rule.getAreaId());
    }

    static String money(int amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,###", symbols).format(amount);
    }

    private static String orDash(String value) {
        return blank(value) ? "—" : Html.esc(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
