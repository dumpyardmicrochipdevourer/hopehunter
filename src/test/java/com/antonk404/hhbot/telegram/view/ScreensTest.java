package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.LetterTemplate;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.domain.repo.LetterTemplateRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Каждый экран собирается с «неудобными» данными и проверяется на то, что Telegram его примет:
 * теги закрыты, пользовательский текст экранирован, callback data в лимите (за это {@link Kb}
 * бросает исключение при сборке).
 */
class ScreensTest {

    private static final String NASTY = "R&D <Java> \"dev\"";

    private final HhAccountService account = mock(HhAccountService.class);
    private final ResponseRuleRepository rules = mock(ResponseRuleRepository.class);
    private final LetterTemplateRepository templates = mock(LetterTemplateRepository.class);
    private final ApplicationLogRepository logs = mock(ApplicationLogRepository.class);
    private final RateLimiter limiter = mock(RateLimiter.class);

    private final MenuScreens menuScreens = new MenuScreens(account, rules, logs, limiter, "Europe/Moscow", "");
    private final AccountScreens accountScreens = new AccountScreens(account);
    private final RuleScreens ruleScreens = new RuleScreens(rules, templates, limiter);
    private final LetterScreens letterScreens = new LetterScreens(templates);

    private BotUser user;
    private ResponseRule rule;
    private LetterTemplate template;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
        rule = new ResponseRule(1L, NASTY);
        ReflectionTestUtils.setField(rule, "id", Long.MAX_VALUE);
        rule.setKeywords(NASTY);
        rule.setMinusWords("senior, <lead>");
        rule.setCompanyBlacklist("A&B");
        rule.setSalaryFrom(200_000);
        rule.setExperience("between1And3");
        rule.setAreaId(88);
        rule.setResume("a".repeat(38), NASTY);
        rule.setLetterTemplateId(5L);
        template = new LetterTemplate(1L, NASTY, "Здравствуйте, [company_name]! <b>не тег</b> & всё");
        ReflectionTestUtils.setField(template, "id", Long.MAX_VALUE);
        when(templates.findById(5L)).thenReturn(Optional.of(template));
        when(templates.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(template));
        when(rules.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(rule));
    }

    /** Простая проверка на то, что отвергает Telegram: незакрытый или перепутанный тег. */
    private static void assertValid(Screen screen) {
        Deque<String> open = new ArrayDeque<>();
        Matcher tag = Pattern.compile("<(/?)(b|i|code|blockquote|a)(?: [^>]*)?>").matcher(screen.text());
        while (tag.find()) {
            if (tag.group(1).isEmpty()) {
                open.push(tag.group(2));
            } else {
                assertEquals(open.isEmpty() ? null : open.pop(), tag.group(2), screen.text());
            }
        }
        assertTrue(open.isEmpty(), "unclosed " + open + " in " + screen.text());
        String withoutTags = tag.reset().replaceAll("");
        assertFalse(withoutTags.contains("<") || withoutTags.contains(">"), "raw angle bracket in " + screen.text());
        assertFalse(Pattern.compile("&(?!amp;|lt;|gt;|quot;)").matcher(screen.text()).find(),
                "raw ampersand in " + screen.text());
        assertTrue(screen.text().length() <= 4096, "too long: " + screen.text().length());
    }

    @Test
    void everyRuleScreenIsValid() {
        List<HhResume> resumes = List.of(new HhResume("a".repeat(38), NASTY));
        HhVacancy vacancy = new HhVacancy("1", NASTY, NASTY, "от 1 ₽", "Москва", "https://hh.ru/vacancy/1", true, true);

        for (Screen screen : List.of(
                ruleScreens.list(user), ruleScreens.rule(rule), ruleScreens.search(rule), ruleScreens.reply(rule),
                ruleScreens.how(rule), ruleScreens.area(rule), ruleScreens.experience(rule), ruleScreens.salary(rule),
                ruleScreens.limit(rule), ruleScreens.resumes(rule, resumes, true), ruleScreens.resumes(rule, resumes, false),
                ruleScreens.letters(user, rule), ruleScreens.wizardMode(rule), ruleScreens.wizardDone(rule),
                ruleScreens.delete(rule), ruleScreens.preview(rule, List.of(vacancy, vacancy), 8),
                VacancyView.card(NASTY, vacancy, NASTY))) {
            assertValid(screen);
        }
    }

    @Test
    void everyOtherScreenIsValid() {
        when(account.session(1L)).thenReturn(Optional.of(new HhSession(1L, "enc", NASTY)));
        HhProfile profile = new HhProfile(NASTY, List.of(new HhResume("h", NASTY)));

        for (Screen screen : List.of(
                menuScreens.home(user), menuScreens.stats(user),
                MenuScreens.retry("Нужно число, например <code>88</code>.", "r:1:gs"),
                MenuScreens.problem("hh не отвечает.", "Ещё раз", "acc:resumes", "acc:show"),
                accountScreens.account(user), accountScreens.connectIntro(),
                accountScreens.connectSteps("chrome"), accountScreens.connectSteps("firefox"),
                accountScreens.connectSteps("safari"), accountScreens.connected(profile, false),
                accountScreens.resumes(profile.resumes()),
                letterScreens.list(user), letterScreens.letter(template),
                letterScreens.preview(template, new HhVacancy("1", NASTY, NASTY, "", "", "", false, false), NASTY),
                MenuScreens.prompt(LetterScreens.aliasHelp(), "l:list"))) {
            assertValid(screen);
        }
    }

    @Test
    void newUserSeesStepsAndOneWayForward() {
        when(account.session(1L)).thenReturn(Optional.empty());
        when(rules.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        Screen home = menuScreens.home(user);

        assertTrue(home.text().contains("▫️ Подключить аккаунт hh"));
        assertEquals(1, home.keyboard().getKeyboard().size());
        assertEquals(Cb.CONNECT, home.keyboard().getKeyboard().getFirst().getFirst().getCallbackData());
    }

    @Test
    void configuredUserSeesStatusAndFullMenu() {
        rule.setEnabled(true);
        when(account.session(1L)).thenReturn(Optional.of(new HhSession(1L, "enc", "Антон")));

        Screen home = menuScreens.home(user);

        assertTrue(home.text().contains("🟢 Работаю"));
        assertFalse(home.text().contains("три шага"));
        assertEquals(3, home.keyboard().getKeyboard().size());
    }

    @Test
    void appButtonComesFirstWhenTheAppIsConfigured() {
        when(account.session(1L)).thenReturn(Optional.empty());
        MenuScreens withApp = new MenuScreens(account, rules, logs, limiter, "Europe/Moscow", "https://hh.example/app");

        var first = withApp.home(user).keyboard().getKeyboard().getFirst().getFirst();

        assertEquals("https://hh.example/app", first.getWebApp().getUrl());
    }

    @Test
    void ruleSummaryListsOnlyFiltersThatAreSet() {
        ResponseRule bare = new ResponseRule(1L, "java");
        ReflectionTestUtils.setField(bare, "id", 2L);

        assertTrue(ruleScreens.rule(bare).text().contains("любой регион\n"));
        assertTrue(ruleScreens.rule(rule).text()
                .contains("регион 88 · от 200 000 ₽ · опыт 1–3 года · минус-слов: 2 · компаний в стоп-листе: 1"));
    }
}
