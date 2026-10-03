package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.dto.MeView;
import com.antonk404.hhbot.domain.dto.StatsView;
import com.antonk404.hhbot.service.DashboardService;
import com.antonk404.hhbot.service.WhitelistService;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final DashboardService dashboardService;
    private final WhitelistService whitelistService;

    public MeController(DashboardService dashboardService, WhitelistService whitelistService) {
        this.dashboardService = dashboardService;
        this.whitelistService = whitelistService;
    }

    @GetMapping("/me")
    public MeView me(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return dashboardService.me(user);
    }

    @PutMapping("/me/pause")
    public MeView pause(@RequestAttribute(AuthInterceptor.USER) BotUser user, @RequestBody PauseRequest request) {
        whitelistService.setPaused(user, request.paused());
        return dashboardService.me(user);
    }

    @GetMapping("/stats")
    public StatsView stats(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return dashboardService.stats(user);
    }
}
