package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.service.util.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Проверка после капчи: что она снимает, чего не снимает и когда вообще не трогает hh. */
class CaptchaServiceTest {

    private static final HhSessionCookies COOKIES = HhSessionCookies.parse("hhtoken=tok; _xsrf=xs");

    private final HhGateway gateway = mock(HhGateway.class);
    private final HhAccountService account = mock(HhAccountService.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final CaptchaService service = new CaptchaService(gateway, account, limiter);

    private BotUser user;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(limiter.stop(1L)).thenReturn(Optional.of(RateLimiter.Stop.CAPTCHA));
        when(limiter.allowRecheck(1L)).thenReturn(true);
        when(account.cookies(1L)).thenReturn(Optional.of(COOKIES));
    }

    @Test
    void passedCaptchaLiftsTheStopSameDay() {
        when(gateway.probe(COOKIES)).thenReturn(HhGateway.Probe.OK);

        assertEquals(CaptchaService.Recheck.GONE, service.recheck(user));
        verify(limiter).clearStop(1L);
    }

    @Test
    void captchaStillThereKeepsTheStop() {
        when(gateway.probe(COOKIES)).thenReturn(HhGateway.Probe.CAPTCHA);

        assertEquals(CaptchaService.Recheck.STILL_THERE, service.recheck(user));
        verify(limiter, never()).clearStop(any());
    }

    /** Суточный потолок hh капчей не снимается - и спрашивать об этом hh незачем. */
    @Test
    void hhDailyLimitIsNotLiftedAndHhIsNotAsked() {
        when(limiter.stop(1L)).thenReturn(Optional.of(RateLimiter.Stop.HH_LIMIT));

        assertEquals(CaptchaService.Recheck.NOTHING_TO_LIFT, service.recheck(user));

        when(limiter.stop(1L)).thenReturn(Optional.empty());
        assertEquals(CaptchaService.Recheck.NOTHING_TO_LIFT, service.recheck(user));

        verifyNoInteractions(gateway);
        verify(limiter, never()).clearStop(any());
    }

    @Test
    void expiredSessionIsMarkedNotMistakenForCaptcha() {
        when(gateway.probe(COOKIES)).thenReturn(HhGateway.Probe.EXPIRED);

        assertEquals(CaptchaService.Recheck.SESSION_EXPIRED, service.recheck(user));
        verify(account).markExpired(1L);
        verify(limiter, never()).clearStop(any());
    }

    @Test
    void withoutCookiesThereIsNothingToAskWith() {
        when(account.cookies(1L)).thenReturn(Optional.empty());

        assertEquals(CaptchaService.Recheck.SESSION_EXPIRED, service.recheck(user));
        verifyNoInteractions(gateway);
    }

    /** Нажатия кнопки - бесплатные, запросы к hh - нет: второе за минуту до сайта не доходит. */
    @Test
    void tooFrequentPressesDoNotReachHh() {
        when(limiter.allowRecheck(1L)).thenReturn(false);

        assertEquals(CaptchaService.Recheck.TOO_SOON, service.recheck(user));
        verifyNoInteractions(gateway);
    }
}
