package com.antonk404.hhbot.telegram.view;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.SessionState;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.service.HhAccountService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.antonk404.hhbot.telegram.view.Kb.btn;

/** Раздел «Аккаунт hh» и пошаговое подключение. */
@Component
public class AccountScreens {

    /** Код браузера в callback data → подпись кнопки. */
    private static final Map<String, String> BROWSERS = new LinkedHashMap<>();

    static {
        BROWSERS.put("chrome", "Chrome · Яндекс · Edge");
        BROWSERS.put("firefox", "Firefox");
        BROWSERS.put("safari", "Safari");
    }

    private final HhAccountService hhAccountService;

    public AccountScreens(HhAccountService hhAccountService) {
        this.hhAccountService = hhAccountService;
    }

    public Screen account(BotUser user) {
        Optional<HhSession> session = hhAccountService.session(user.getId());
        Kb keyboard = Kb.of();
        String text;
        if (session.isEmpty()) {
            text = "<b>Аккаунт hh</b>\n\nНе подключён. Без него мне нечем искать и откликаться.";
            keyboard.row(btn("🔑 Подключить", Cb.CONNECT));
        } else if (session.get().getState() == SessionState.EXPIRED) {
            text = "<b>Аккаунт hh</b>\n\n⚠️ Сессия истекла: hh разлогинил её сам или ты вышел на сайте. "
                    + "Отклики стоят, пока не войдёшь заново.";
            keyboard.row(btn("🔑 Войти заново", Cb.CONNECT)).row(btn("Отключить аккаунт", Cb.LOGOUT));
        } else {
            String name = session.get().getOwnerName();
            text = "<b>Аккаунт hh</b>\n\n✅ Подключён" + (name == null || name.isBlank() ? "" : ": " + Html.esc(name)) + ".";
            keyboard.row(btn("📄 Мои резюме", Cb.RESUMES))
                    .row(btn("Обновить сессию", Cb.CONNECT), btn("Отключить", Cb.LOGOUT));
        }
        return new Screen(text, keyboard.row(btn("🏠 Меню", Cb.HOME)).build());
    }

    /**
     * Первый экран подключения: что будет и сколько займёт, до всяких инструкций.
     * Человек, которому с порога говорят «открой инструменты разработчика», закрывает бота.
     */
    public Screen connectIntro() {
        Kb keyboard = Kb.of();
        BROWSERS.forEach((code, label) -> keyboard.row(btn(label, Cb.CONNECT_HOW + code)));
        return new Screen("""
                <b>Подключение hh</b>

                Займёт пару минут, нужен компьютер с браузером, где ты уже вошёл на hh.ru.

                Пароль я не спрашиваю. Вместо него нужна одна строчка из браузера - по ней hh узнаёт, \
                что это ты. Покажу, где её взять.

                В каком браузере у тебя открыт hh?""",
                keyboard.row(btn("Отмена", Cb.ACCOUNT)).build());
    }

    /** Шаги под конкретный браузер - с теми названиями пунктов, которые человек увидит на экране. */
    public Screen connectSteps(String browser) {
        String steps = switch (browser) {
            case "firefox" -> """
                    1. Открой hh.ru и убедись, что ты вошёл.
                    2. Нажми <b>F12</b> (на Mac - <b>⌥⌘I</b>). Внизу откроется панель.
                    3. В ней выбери вкладку <b>Хранилище</b> (Storage).
                    4. Слева раскрой <b>Куки</b> и нажми на <b>https://hh.ru</b>.
                    5. В таблице найди строку <code>hhtoken</code>, дважды кликни по её значению и скопируй.""";
            case "safari" -> """
                    1. Один раз включи меню разработчика: <b>Safari → Настройки → Дополнения</b>, \
                    галочка «Показывать функции для веб-разработчиков».
                    2. Открой hh.ru и убедись, что ты вошёл.
                    3. Нажми <b>⌥⌘I</b>. Откроется панель.
                    4. В ней выбери вкладку <b>Хранилище</b>, слева - <b>Cookies</b>.
                    5. В таблице найди строку <code>hhtoken</code> и скопируй её значение.""";
            default -> """
                    1. Открой hh.ru и убедись, что ты вошёл.
                    2. Нажми <b>F12</b> (на Mac - <b>⌥⌘I</b>). Сбоку или внизу откроется панель.
                    3. Вверху панели выбери вкладку <b>Application</b>. Если её не видно - нажми <b>»</b>, \
                    она спрятана там.
                    4. Слева раскрой <b>Cookies</b> и нажми на <b>https://hh.ru</b>.
                    5. В таблице найди строку <code>hhtoken</code>, дважды кликни по значению в колонке \
                    Value и скопируй.""";
        };
        return new Screen("<b>Подключение hh · " + Html.esc(BROWSERS.getOrDefault(browser, BROWSERS.get("chrome")))
                + "</b>\n\n" + steps + """


                <b>Пришли скопированное сюда сообщением.</b> Формат не важен: можно значение, можно \
                всю строку из таблицы.

                🔒 Это ключ от твоего аккаунта. Твоё сообщение я сразу удалю из чата, а у себя храню \
                его зашифрованным.""",
                Kb.of().row(btn("Другой браузер", Cb.CONNECT), btn("Отмена", Cb.ACCOUNT)).build());
    }

    /** Сразу после подключения: что получилось и куда дальше. */
    public Screen connected(HhProfile profile, boolean hasRules) {
        StringBuilder text = new StringBuilder("✅ <b>Подключил");
        if (!profile.ownerName().isBlank()) {
            text.append(", ").append(Html.esc(profile.ownerName()));
        }
        text.append(".</b>\n\n");
        if (profile.resumes().isEmpty()) {
            text.append("⚠️ На hh у тебя нет ни одного резюме. Создай его на сайте - откликаться без него нечем.");
        } else {
            text.append("Резюме на hh: ").append(profile.resumes().size()).append('.');
        }
        Kb keyboard = Kb.of();
        if (!hasRules) {
            text.append("\n\nТеперь правило: что искать и чем откликаться.");
            keyboard.row(btn("➕ Создать первое правило", Cb.RULE_NEW));
        }
        return new Screen(text.toString(), keyboard.row(btn("🏠 Меню", Cb.HOME)).build());
    }

    public Screen resumes(List<HhResume> resumes) {
        StringBuilder text = new StringBuilder("<b>Резюме на hh</b>\n");
        if (resumes.isEmpty()) {
            text.append("\nНи одного. Создай резюме на hh.ru - откликаться без него нечем.");
        }
        resumes.forEach(resume -> text.append("\n• ").append(Html.esc(resume.title())));
        return new Screen(text.toString(), Kb.of().row(btn("« Назад", Cb.ACCOUNT)).build());
    }

    public static boolean isBrowser(String code) {
        return BROWSERS.containsKey(code);
    }
}
