package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.ApplicationLog;
import com.antonk404.hhbot.domain.ApplicationState;
import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.ResponseRule;
import com.antonk404.hhbot.domain.SessionState;
import com.antonk404.hhbot.domain.repo.ApplicationLogRepository;
import com.antonk404.hhbot.domain.repo.ResponseRuleRepository;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.antonk404.hhbot.telegram.view.Kb.btn;

/**
 * Главное меню, статистика и общие экраны-заготовки.
 *
 * <p>Экран - функция от состояния в базе, без параметров «что только что произошло». Поэтому
 * любой обработчик после любого действия просто перерисовывает нужный экран, и показанное не
 * может разойтись с сохранённым.
 */
@Component
public class MenuScreens {

    private final HhAccountService hhAccountService;
    private final ResponseRuleRepository responseRuleRepository;
    private final ApplicationLogRepository applicationLogRepository;
    private final RateLimiter rateLimiter;
    private final ZoneId zone;

    public MenuScreens(
            HhAccountService hhAccountService,
            ResponseRuleRepository responseRuleRepository,
            ApplicationLogRepository applicationLogRepository,
            RateLimiter rateLimiter,
            @Value("${bot.timezone}") String timezone) {
        this.hhAccountService = hhAccountService;
        this.responseRuleRepository = responseRuleRepository;
        this.applicationLogRepository = applicationLogRepository;
        this.rateLimiter = rateLimiter;
        this.zone = ZoneId.of(timezone);
    }

    /**
     * Пока бот не настроен, вместо меню - три шага и одна кнопка, ведущая к следующему.
     * Полное меню новичку ничего не говорит: пять разделов, и непонятно, с какого начинать.
     */
    public Screen home(BotUser user) {
        Optional<HhSession> session = hhAccountService.session(user.getId());
        boolean connected = session.map(found -> found.getState() == SessionState.ACTIVE).orElse(false);
        List<ResponseRule> rules = responseRuleRepository.findByUserIdOrderByIdAsc(user.getId());
        long enabled = rules.stream().filter(ResponseRule::isEnabled).count();

        if (connected && enabled > 0) {
            return status(user, session.get(), rules.size(), enabled);
        }

        StringBuilder text = new StringBuilder("<b>Автоотклики на hh.ru</b>\n\n");
        text.append("Я ищу вакансии по твоим правилам и откликаюсь твоим резюме. До первого отклика три шага:\n\n");
        text.append(step(connected, "Подключить аккаунт hh"));
        text.append(step(!rules.isEmpty(), "Создать правило: что искать и чем откликаться"));
        text.append(step(enabled > 0, "Включить правило"));
        if (session.isPresent() && !connected) {
            text.append("\n⚠️ Сессия hh истекла - нужно войти заново.");
        }

        Kb keyboard = Kb.of();
        if (!connected) {
            keyboard.row(btn("🔑 Подключить hh", Cb.CONNECT));
        } else if (rules.isEmpty()) {
            keyboard.row(btn("➕ Создать правило", Cb.RULE_NEW));
        } else {
            keyboard.row(btn("📋 Открыть правило", Cb.rule(rules.getFirst().getId(), "show")));
        }
        // Остальные разделы - только когда в них уже есть на что смотреть.
        if (connected || !rules.isEmpty()) {
            sections(keyboard, user);
        }
        return new Screen(text.toString(), keyboard.build());
    }

    private static String step(boolean done, String title) {
        return (done ? "✅ " : "▫️ ") + title + "\n";
    }

    private Screen status(BotUser user, HhSession session, int rules, long enabled) {
        String name = session.getOwnerName();
        StringBuilder text = new StringBuilder("<b>Автоотклики на hh.ru</b>\n\n");
        if (user.isPaused()) {
            text.append("⏸ <b>Пауза.</b> Ничего не ищу и не откликаюсь.\n\n");
        } else if (rateLimiter.isStopped(user.getId())) {
            text.append("⛔ <b>Отклики остановлены до завтра.</b>\n\n");
        } else {
            text.append("🟢 Работаю.\n\n");
        }
        text.append("Аккаунт: ").append(name == null || name.isBlank() ? "подключён" : Html.esc(name)).append('\n');
        text.append("Правила: ").append(enabled).append(" из ").append(rules).append(" включено\n");
        text.append("Откликов сегодня: <b>").append(sentToday(user)).append("</b>");
        return new Screen(text.toString(), sections(Kb.of(), user).build());
    }

    private static Kb sections(Kb keyboard, BotUser user) {
        return keyboard
                .row(btn("📋 Правила", Cb.RULES), btn("✉️ Письма", Cb.LETTERS))
                .row(btn("📊 Статистика", Cb.STATS), btn("🔑 Аккаунт hh", Cb.ACCOUNT))
                .row(btn(user.isPaused() ? "▶️ Снять паузу" : "⏸ Пауза", Cb.PAUSE));
    }

    public Screen stats(BotUser user) {
        Long id = user.getId();
        StringBuilder text = new StringBuilder("<b>Статистика</b>\n\n");
        text.append("Откликов сегодня: <b>").append(sentToday(user)).append("</b>\n");
        text.append("Откликов всего: <b>")
                .append(applicationLogRepository.countByUserIdAndState(id, ApplicationState.SENT)).append("</b>\n");
        text.append("Прислал тебе ссылкой: ")
                .append(applicationLogRepository.countByUserIdAndState(id, ApplicationState.MANUAL)).append('\n');
        text.append("Пропущено: ").append(applicationLogRepository.countByUserIdAndState(id, ApplicationState.SKIPPED));

        List<ApplicationLog> last = applicationLogRepository
                .findTop10ByUserIdAndStateOrderByCreatedAtDesc(id, ApplicationState.SENT);
        if (!last.isEmpty()) {
            text.append("\n\n<b>Последние отклики</b>");
            for (ApplicationLog log : last) {
                LocalDate date = log.getCreatedAt().atZone(zone).toLocalDate();
                text.append("\n").append(String.format("%02d.%02d", date.getDayOfMonth(), date.getMonthValue()))
                        .append(" · ").append(Html.esc(log.getVacancyName()))
                        .append(" — ").append(Html.esc(log.getCompany()));
            }
        }
        return new Screen(text.toString(), Kb.of().row(btn("🏠 Меню", Cb.HOME)).build());
    }

    private long sentToday(BotUser user) {
        Instant midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return applicationLogRepository.countByUserIdAndStateAndCreatedAtAfter(
                user.getId(), ApplicationState.SENT, midnight);
    }

    /** Вопрос, на который человек отвечает текстом. Единственная кнопка - отмена. */
    public static Screen prompt(String html, String cancelData) {
        return new Screen(html, Kb.of().row(btn("Отмена", cancelData)).build());
    }

    /** Тот же вопрос после неподходящего ответа: что не так и что прислать. */
    public static Screen retry(String html, String cancelData) {
        return prompt("⚠️ " + html + "\n\nПришли ещё раз или нажми «Отмена».", cancelData);
    }

    /**
     * Отказ с кнопкой следующего шага. «Сначала подключи аккаунт» без кнопки «Подключить» -
     * это задача человеку найти нужный раздел самому.
     */
    public static Screen problem(String html, String actionLabel, String actionData, String backData) {
        return new Screen("⚠️ " + html, Kb.of()
                .row(btn(actionLabel, actionData))
                .row(btn("« Назад", backData))
                .build());
    }
}
