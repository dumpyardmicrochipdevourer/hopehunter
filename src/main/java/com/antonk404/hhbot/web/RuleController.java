package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.dto.RuleData;
import com.antonk404.hhbot.domain.dto.RuleView;
import com.antonk404.hhbot.service.RuleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    public record EnabledRequest(boolean enabled) {
    }

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<RuleView> list(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return ruleService.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView create(@RequestAttribute(AuthInterceptor.USER) BotUser user, @RequestBody RuleData data) {
        return ruleService.create(user, data);
    }

    @GetMapping("/{id}")
    public RuleView get(@RequestAttribute(AuthInterceptor.USER) BotUser user, @PathVariable Long id) {
        return ruleService.get(user, id);
    }

    @PutMapping("/{id}")
    public RuleView update(
            @RequestAttribute(AuthInterceptor.USER) BotUser user, @PathVariable Long id, @RequestBody RuleData data) {
        return ruleService.update(user, id, data);
    }

    @PutMapping("/{id}/enabled")
    public RuleView setEnabled(
            @RequestAttribute(AuthInterceptor.USER) BotUser user, @PathVariable Long id,
            @RequestBody EnabledRequest request) {
        return ruleService.setEnabled(user, id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER) BotUser user, @PathVariable Long id) {
        ruleService.delete(user, id);
    }

    /** Что правило найдёт прямо сейчас. Ничего не отправляет. */
    @GetMapping("/{id}/preview")
    public List<HhVacancy> preview(@RequestAttribute(AuthInterceptor.USER) BotUser user, @PathVariable Long id) {
        return ruleService.preview(user, id);
    }
}
