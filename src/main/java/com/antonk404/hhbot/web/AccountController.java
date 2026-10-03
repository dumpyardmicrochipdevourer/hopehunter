package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.dto.AccountView;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.service.DashboardService;
import com.antonk404.hhbot.service.HhAccountService;
import com.antonk404.hhbot.service.exceptions.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    /** @param value значение {@code hhtoken} или строка с куками в любом виде, как из браузера */
    public record CookiesRequest(String value) {
    }

    private final HhAccountService hhAccountService;
    private final DashboardService dashboardService;

    public AccountController(HhAccountService hhAccountService, DashboardService dashboardService) {
        this.hhAccountService = hhAccountService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public AccountView account(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return dashboardService.account(user);
    }

    /** Проверяет куки на живом hh и только потом сохраняет. */
    @PutMapping("/cookies")
    public AccountView connect(@RequestAttribute(AuthInterceptor.USER) BotUser user, @RequestBody CookiesRequest request) {
        try {
            hhAccountService.connect(user.getId(), request.value());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("value", "Не нашёл здесь значение hhtoken.");
        }
        return dashboardService.account(user);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        hhAccountService.disconnect(user.getId());
    }

    @GetMapping("/resumes")
    public List<HhResume> resumes(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return hhAccountService.profile(user.getId()).resumes();
    }
}
