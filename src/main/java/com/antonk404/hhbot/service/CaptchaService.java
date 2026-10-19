package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Капча - просьба hh доказать, что по сайту ходит человек. Доказывает человек, в своём браузере;
 * бот умеет только спросить и проверить, пускает ли hh снова.
 *
 * <p>Без этой проверки капча стоила суток: стоп висел до полуночи, даже если капчу прошли через
 * минуту. Проверка - одна обычная страница, не отклик: лишних попыток отклика она не тратит.
 */
@Service
public class CaptchaService {

    /** Чем закончилась проверка. Фразы для человека - на стороне бота и API, они разные. */
    public enum Recheck {
        /** hh снова пускает, стоп снят - отклики пойдут в этот же день. */
        GONE,
        STILL_THERE,
        /** Проверять чаще раза в минуту нечего: ответ hh за минуту не меняется. */
        TOO_SOON,
        /** Стоп не из-за капчи (суточный лимит hh) или его уже нет. */
        NOTHING_TO_LIFT,
        SESSION_EXPIRED
    }

    private final HhGateway hhGateway;
    private final HhAccountService hhAccountService;
    private final RateLimiter rateLimiter;

    public CaptchaService(HhGateway hhGateway, HhAccountService hhAccountService, RateLimiter rateLimiter) {
        this.hhGateway = hhGateway;
        this.hhAccountService = hhAccountService;
        this.rateLimiter = rateLimiter;
    }

    /** @throws com.antonk404.hhbot.hh.exceptions.HhException hh не ответил - состояние неизвестно */
    public Recheck recheck(BotUser user) {
        Long userId = user.getId();
        if (rateLimiter.stop(userId).orElse(null) != RateLimiter.Stop.CAPTCHA) {
            return Recheck.NOTHING_TO_LIFT;
        }
        Optional<HhSessionCookies> cookies = hhAccountService.cookies(userId);
        if (cookies.isEmpty()) {
            return Recheck.SESSION_EXPIRED;
        }
        if (!rateLimiter.allowRecheck(userId)) {
            return Recheck.TOO_SOON;
        }
        return switch (hhGateway.probe(cookies.get())) {
            case OK -> {
                rateLimiter.clearStop(userId);
                yield Recheck.GONE;
            }
            case CAPTCHA -> Recheck.STILL_THERE;
            case EXPIRED -> {
                hhAccountService.markExpired(userId);
                yield Recheck.SESSION_EXPIRED;
            }
        };
    }
}
