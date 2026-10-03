package com.antonk404.hhbot.telegram;

import com.antonk404.hhbot.domain.ApplicationLog;
import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.SessionState;
import com.antonk404.hhbot.hh.HhResume;
import com.antonk404.hhbot.repo.ApplicationLogRepository;
import com.antonk404.hhbot.repo.LetterTemplateRepository;
import com.antonk404.hhbot.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.LetterRenderer;
import com.antonk404.hhbot.service.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.antonk404.hhbot.telegram.Kb.btn;

/**
 * Все экраны меню в одном месте.
 *
 * <p>Экран - функция от состояния в базе, без параметров «что только что произошло». Поэтому
 * любой обработчик после любого действия просто перерисовывает нужный экран, и показанное не
 * может разойтись с сохранённым.
 */
@Component
public class Screens {

    public static final String HOME = "m:home";
    public static final String STATS = "m:stats";
    public static final String PAUSE = "m:pause";
    public static final String ACCOUNT = "acc:show";
    public static final String RULES = "r:list";
    public static final String RULE_NEW = "r:new";
    public static final String LETTERS = "l:list";
    public static final String LETTER_NEW = "l:new";

    /** Регионы, которые нужны почти всем. Остальные вводятся числом - id из адреса поиска hh. */
    public static final Map<Integer, String> AREAS = new LinkedHashMap<>();

    public static final Map<String, String> EXPERIENCE = new LinkedHashMap<>();

    static {
        AREAS.put(1, "Москва");
        AREAS.put(2, "Санкт-Петербург");
        AREAS.put(113, "Россия");
        EXPERIENCE.put("noExperience", "без опыта");
        EXPERIENCE.put("between1And3", "1–3 года");
        EXPERIENCE.put("between3And6", "3–6 лет");
        EXPERIENCE.put("moreThan6", "больше 6 лет");
    }

    private final HhAccountService hhAccountService;
    private final ResponseRuleRepository responseRuleRepository;
    private final LetterTemplateRepository letterTemplateRepository;
    private final ApplicationLogRepository applicationLogRepository;
    private final RateLimiter rateLimiter;
    private final ZoneId zone;

    public Screens(
            HhAccountService hhAccountService,
            ResponseRuleRepository responseRuleRepository,
            LetterTemplateRepository letterTemplateRepository,
            ApplicationLogRepository applicationLogRepository,
            RateLimiter rateLimiter,
            @Value("${bot.timezone}") String timezone) {
        this.hhAccountService = hhAccountService;
        this.responseRuleRepository = responseRuleRepository;
        this.letterTemplateRepository = letterTemplateRepository;
        this.applicationLogRepository = applicationLogRepository;
        this.rateLimiter = rateLimiter;
        this.zone = ZoneId.of(timezone);
    }

    public Screen home(BotUser user) {
        List<ResponseRule> rules = responseRuleRepository.findByUserIdOrderByIdAsc(user.getId());
        long enabled = rules.stream().filter(ResponseRule::isEnabled).count();

        StringBuilder text = new StringBuilder("Автоотклики на hh.ru\n\n");
        text.append("Аккаунт hh: ").append(sessionLine(user)).append('\n');
        text.append("Правила: ").append(rules.size()).append(", включено ").append(enabled).append('\n');
        text.append("Откликов сегодня: ").append(sentToday(user));
        if (user.isPaused()) {
            text.append("\n\n⏸ Пауза: бот ничего не ищет и не откликается.");
        } else if (rateLimiter.isStopped(user.getId())) {
            text.append("\n\n⛔ Отклики остановлены до завтра.");
        }

        return new Screen(text.toString(), Kb.of()
                .row(btn("🔑 Аккаунт hh", ACCOUNT), btn("📋 Правила", RULES))
                .row(btn("✉️ Письма", LETTERS), btn("📊 Статистика", STATS))
                .row(btn(user.isPaused() ? "▶️ Снять паузу" : "⏸ Пауза", PAUSE))
                .build());
    }

    private String sessionLine(BotUser user) {
        Optional<HhSession> session = hhAccountService.session(user.getId());
        if (session.isEmpty()) {
            return "не подключён";
        }
        if (session.get().getState() == SessionState.EXPIRED) {
            return "сессия истекла";
        }
        String name = session.get().getOwnerName();
        return name == null || name.isBlank() ? "подключён" : "подключён, " + name;
    }

    public Screen account(BotUser user) {
        Optional<HhSession> session = hhAccountService.session(user.getId());
        Kb keyboard = Kb.of();
        String text;
        if (session.isEmpty()) {
            text = "Аккаунт hh не подключён.\n\nБоту нужна твоя сессия на сайте: он откликается от твоего "
                    + "имени теми же запросами, что и браузер. Пароль боту не нужен и он его не просит.";
            keyboard.row(btn("Подключить", "acc:cookies"));
        } else if (session.get().getState() == SessionState.EXPIRED) {
            text = "Сессия hh истекла. Отклики стоят, пока не войдёшь заново.";
            keyboard.row(btn("Войти заново", "acc:cookies")).row(btn("Отключить аккаунт", "acc:logout"));
        } else {
            text = "Аккаунт hh: " + sessionLine(user) + ".";
            keyboard.row(btn("Мои резюме", "acc:resumes"))
                    .row(btn("Обновить сессию", "acc:cookies"), btn("Отключить", "acc:logout"));
        }
        return new Screen(text, keyboard.row(btn("« Назад", HOME)).build());
    }

    public Screen cookiesPrompt() {
        return new Screen("""
                Как подключить аккаунт:

                1. Открой hh.ru в браузере на компьютере и войди.
                2. Открой инструменты разработчика (F12) → Application → Cookies → https://hh.ru.
                3. Скопируй значение куки hhtoken и пришли его сюда одним сообщением.

                Можно прислать и всю строку Cookie целиком из любого запроса во вкладке Network.

                Это значение - ключ от твоего аккаунта: никому другому его не отправляй. Сообщение \
                с ним я удалю из чата, а в базе оно хранится зашифрованным.""",
                Kb.of().row(btn("Отмена", ACCOUNT)).build());
    }

    public Screen resumes(List<HhResume> resumes) {
        StringBuilder text = new StringBuilder("Резюме на hh:\n");
        if (resumes.isEmpty()) {
            text.append("\nНи одного. Создай резюме на hh.ru - откликаться без него нечем.");
        }
        resumes.forEach(resume -> text.append("\n• ").append(resume.title()));
        return new Screen(text.toString(), Kb.of().row(btn("« Назад", ACCOUNT)).build());
    }

    public Screen rules(BotUser user) {
        List<ResponseRule> rules = responseRuleRepository.findByUserIdOrderByIdAsc(user.getId());
        Kb keyboard = Kb.of();
        for (ResponseRule rule : rules) {
            keyboard.row(btn((rule.isEnabled() ? "🟢 " : "⚪️ ") + rule.getName(), rule(rule.getId(), "show")));
        }
        String text = rules.isEmpty()
                ? "Правил пока нет.\n\nПравило - это поиск (ключевые слова, регион, зарплата) плюс резюме и "
                + "письмо, которыми бот откликается на найденное."
                : "Правила отклика. 🟢 включено, ⚪️ выключено.";
        return new Screen(text, keyboard
                .row(btn("➕ Новое правило", RULE_NEW))
                .row(btn("« Назад", HOME))
                .build());
    }

    public Screen rule(ResponseRule rule) {
        Long id = rule.getId();
        String letter = rule.getLetterTemplateId() == null ? null
                : letterTemplateRepository.findById(rule.getLetterTemplateId()).map(LetterTemplate::getName).orElse(null);

        StringBuilder text = new StringBuilder();
        text.append(rule.isEnabled() ? "🟢 " : "⚪️ ").append(rule.getName()).append("\n\n");
        text.append("Ключевые слова: ").append(orDash(rule.getKeywords())).append('\n');
        text.append("Искать: ").append(rule.isTitleOnly() ? "только в названии" : "в названии и описании").append('\n');
        text.append("Минус-слова: ").append(orDash(rule.getMinusWords())).append('\n');
        text.append("Регион: ").append(areaName(rule.getAreaId())).append('\n');
        text.append("Зарплата от: ").append(rule.getSalaryFrom() == null ? "любая" : rule.getSalaryFrom()).append('\n');
        text.append("Опыт: ").append(rule.getExperience() == null ? "любой" : EXPERIENCE.get(rule.getExperience())).append('\n');
        text.append("Формат: ").append(rule.isRemoteOnly() ? "только удалёнка" : "любой").append('\n');
        text.append("Стоп-лист компаний: ").append(orDash(rule.getCompanyBlacklist())).append("\n\n");
        text.append("Резюме: ").append(orDash(rule.getResumeTitle())).append('\n');
        text.append("Письмо: ").append(letter == null ? "без письма" : letter).append('\n');
        text.append("Режим: ").append(rule.getMode() == RuleMode.AUTO
                ? "авто - откликаюсь сам" : "с подтверждением - присылаю карточку").append('\n');
        text.append("Лимит: ").append(rateLimiter.sentToday(rule)).append(" из ").append(rule.getDailyLimit())
                .append(" откликов сегодня");
        if (!rule.isReady()) {
            text.append("\n\nЧтобы включить, задай ключевые слова и выбери резюме.");
        }

        return new Screen(text.toString(), Kb.of()
                .row(btn("Ключевые слова", rule(id, "kw")), btn("Минус-слова", rule(id, "minus")))
                .row(btn("Регион", rule(id, "area")), btn("Зарплата", rule(id, "salary")))
                .row(btn("Опыт", rule(id, "exp")), btn(rule.isRemoteOnly() ? "Удалёнка: да" : "Удалёнка: нет", rule(id, "remote")))
                .row(btn(rule.isTitleOnly() ? "Искать: название" : "Искать: везде", rule(id, "title")),
                        btn("Стоп-лист", rule(id, "bl")))
                .row(btn("Резюме", rule(id, "resume")), btn("Письмо", rule(id, "letter")))
                .row(btn(rule.getMode() == RuleMode.AUTO ? "Режим: авто" : "Режим: подтверждение", rule(id, "mode")),
                        btn("Лимит в день", rule(id, "limit")))
                .row(btn("🔍 Проверить сейчас", rule(id, "test")))
                .row(btn(rule.isEnabled() ? "⚪️ Выключить" : "🟢 Включить", rule(id, "toggle")),
                        btn("Переименовать", rule(id, "name")))
                .row(btn("🗑 Удалить", rule(id, "del")), btn("« К правилам", RULES))
                .build());
    }

    public Screen ruleArea(ResponseRule rule) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        AREAS.forEach((areaId, name) -> keyboard.row(btn(name, rule(id, "area:" + areaId))));
        return new Screen("Регион поиска. Сейчас: " + areaName(rule.getAreaId()) + ".", keyboard
                .row(btn("Любой", rule(id, "area:0")), btn("Другой (ввести id)", rule(id, "areaid")))
                .row(btn("« Назад", rule(id, "show")))
                .build());
    }

    public Screen ruleExperience(ResponseRule rule) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        EXPERIENCE.forEach((code, name) -> keyboard.row(btn(name, rule(id, "exp:" + code))));
        return new Screen("Требуемый опыт в вакансии.", keyboard
                .row(btn("Любой", rule(id, "exp:any")))
                .row(btn("« Назад", rule(id, "show")))
                .build());
    }

    public Screen ruleResumes(ResponseRule rule, List<HhResume> resumes) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        resumes.forEach(resume -> keyboard.row(btn(resume.title(), rule(id, "rs:" + resume.hash()))));
        String text = resumes.isEmpty()
                ? "На hh нет ни одного резюме. Создай его на сайте и вернись сюда."
                : "Каким резюме откликаться по этому правилу?";
        return new Screen(text, keyboard.row(btn("« Назад", rule(id, "show"))).build());
    }

    public Screen ruleLetters(BotUser user, ResponseRule rule) {
        Long id = rule.getId();
        Kb keyboard = Kb.of();
        List<LetterTemplate> templates = letterTemplateRepository.findByUserIdOrderByIdAsc(user.getId());
        templates.forEach(template -> keyboard.row(btn(template.getName(), rule(id, "lt:" + template.getId()))));
        String text = templates.isEmpty()
                ? "Писем пока нет. Создай шаблон в разделе «Письма» и выбери его здесь."
                : "Какое письмо прикладывать к отклику?";
        return new Screen(text, keyboard
                .row(btn("Без письма", rule(id, "lt:0")))
                .row(btn("« Назад", rule(id, "show")))
                .build());
    }

    public Screen ruleDelete(ResponseRule rule) {
        Long id = rule.getId();
        return new Screen("Удалить правило «" + rule.getName() + "»? Журнал откликов останется.", Kb.of()
                .row(btn("Да, удалить", rule(id, "delok")), btn("Нет", rule(id, "show")))
                .build());
    }

    /** Экран-вопрос для текстового ввода с единственной кнопкой отмены. */
    public Screen prompt(String text, String cancelData) {
        return new Screen(text, Kb.of().row(btn("Отмена", cancelData)).build());
    }

    public Screen letters(BotUser user) {
        Kb keyboard = Kb.of();
        List<LetterTemplate> templates = letterTemplateRepository.findByUserIdOrderByIdAsc(user.getId());
        templates.forEach(template -> keyboard.row(btn(template.getName(), letter(template.getId(), "show"))));
        String text = (templates.isEmpty() ? "Писем пока нет.\n\n" : "Шаблоны сопроводительных писем.\n\n")
                + aliasHelp();
        return new Screen(text, keyboard
                .row(btn("➕ Новое письмо", LETTER_NEW))
                .row(btn("« Назад", HOME))
                .build());
    }

    public static String aliasHelp() {
        return """
                В тексте письма работают алиасы:
                [company_name] - компания
                [vacancy_name] - название вакансии
                [salary] - вилка из вакансии, пусто если не указана
                [city] - город вакансии
                [my_name] - твоё имя из профиля hh""";
    }

    public Screen letter(LetterTemplate template) {
        Long id = template.getId();
        return new Screen("✉️ " + template.getName() + "\n\n" + template.getBody(), Kb.of()
                .row(btn("Изменить текст", letter(id, "edit")), btn("Предпросмотр", letter(id, "pv")))
                .row(btn("🗑 Удалить", letter(id, "del")), btn("« К письмам", LETTERS))
                .build());
    }

    public Screen stats(BotUser user) {
        Long id = user.getId();
        StringBuilder text = new StringBuilder("Статистика\n\n");
        text.append("Откликов сегодня: ").append(sentToday(user)).append('\n');
        text.append("Откликов всего: ").append(applicationLogRepository.countByUserIdAndState(id, ApplicationState.SENT)).append('\n');
        text.append("Ушло тебе вручную: ").append(applicationLogRepository.countByUserIdAndState(id, ApplicationState.MANUAL)).append('\n');
        text.append("Пропущено: ").append(applicationLogRepository.countByUserIdAndState(id, ApplicationState.SKIPPED));

        List<ApplicationLog> last = applicationLogRepository
                .findTop10ByUserIdAndStateOrderByCreatedAtDesc(id, ApplicationState.SENT);
        if (!last.isEmpty()) {
            text.append("\n\nПоследние отклики:");
            for (ApplicationLog log : last) {
                LocalDate date = log.getCreatedAt().atZone(zone).toLocalDate();
                text.append("\n• ").append(date.getDayOfMonth()).append('.').append(String.format("%02d", date.getMonthValue()))
                        .append(" ").append(log.getVacancyName()).append(" - ").append(log.getCompany());
            }
        }
        return new Screen(text.toString(), Kb.of().row(btn("« Назад", HOME)).build());
    }

    private long sentToday(BotUser user) {
        Instant midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return applicationLogRepository.countByUserIdAndStateAndCreatedAtAfter(
                user.getId(), ApplicationState.SENT, midnight);
    }

    public static String rule(Long id, String action) {
        return "r:" + id + ":" + action;
    }

    public static String letter(Long id, String action) {
        return "l:" + id + ":" + action;
    }

    private static String areaName(Integer areaId) {
        if (areaId == null) {
            return "любой";
        }
        return AREAS.getOrDefault(areaId, "id " + areaId);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /** Имена алиасов через запятую - для сообщения об опечатке в шаблоне. */
    public static String aliasList() {
        return "[" + String.join("], [", LetterRenderer.ALIASES) + "]";
    }
}
