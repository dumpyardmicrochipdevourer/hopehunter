package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.dto.MeView;
import com.antonk404.hhbot.domain.dto.StatsView;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.CaptchaService;
import com.antonk404.hhbot.service.DashboardService;
import com.antonk404.hhbot.service.UserService;
import com.antonk404.hhbot.service.exceptions.ValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    public record PauseRequest(boolean paused) {
    }

    public record NotificationsRequest(boolean applied) {
    }

    private final DashboardService dashboardService;
    private final UserService userService;
    private final CaptchaService captchaService;

    public MeController(
            DashboardService dashboardService, UserService userService, CaptchaService captchaService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
        this.captchaService = captchaService;
    }

    @GetMapping("/me")
    public MeView me(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return dashboardService.me(user);
    }

    @PutMapping("/me/pause")
    public MeView pause(@RequestAttribute(AuthInterceptor.USER) BotUser user, @RequestBody PauseRequest request) {
        userService.setPaused(user, request.paused());
        return dashboardService.me(user);
    }

    @PutMapping("/me/notifications")
    public MeView notifications(@RequestAttribute(AuthInterceptor.USER) BotUser user,
                                @RequestBody NotificationsRequest request) {
        userService.setNotifyApplied(user, request.applied());
        return dashboardService.me(user);
    }

    /**
     * «Я прошёл капчу»: если hh снова пускает, стоп снимается сразу. Отказ приходит обычной
     * ошибкой с готовой фразой - приложению остаётся её показать.
     */
    @PostMapping("/me/captcha")
    public MeView captcha(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        switch (captchaService.recheck(user)) {
            case GONE, NOTHING_TO_LIFT -> {
                return dashboardService.me(user);
            }
            case STILL_THERE -> throw new ValidationException(null,
                    "hh всё ещё показывает капчу. Пройди её на hh.ru и нажми ещё раз.");
            case TOO_SOON -> throw new ValidationException(null, "Подожди минуту и нажми ещё раз.");
            default -> throw new HhSessionExpiredException();
        }
    }

    @GetMapping("/stats")
    public StatsView stats(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return dashboardService.stats(user);
    }
}