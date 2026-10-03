package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.RuleMode;
import com.antonk404.hhbot.domain.dto.RuleData;
import com.antonk404.hhbot.domain.dto.RuleView;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.antonk404.hhbot.service.RuleService;
import com.antonk404.hhbot.service.WhitelistService;
import com.antonk404.hhbot.service.exceptions.NotFoundException;
import com.antonk404.hhbot.service.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Контракт API глазами приложения: подпись, коды ответов и вид ошибок. */
class ApiTest {

    private static final String TOKEN = "123456:TEST-TOKEN";

    private final RuleService ruleService = mock(RuleService.class);
    private final WhitelistService whitelistService = mock(WhitelistService.class);

    private MockMvc mvc;
    private BotUser user;
    private String auth;

    @BeforeEach
    void setUp() {
        user = new BotUser("anton", "anton");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(whitelistService.resolve(42L, "anton")).thenReturn(Optional.of(user));

        InitDataValidator validator = new InitDataValidator(TOKEN, Duration.ofHours(24), Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new RuleController(ruleService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new AuthInterceptor(validator, whitelistService))
                .build();
        auth = "tma " + InitDataValidatorTest.sign(TOKEN, Instant.now(), "{\"id\":42,\"username\":\"anton\"}");
    }

    private static RuleView view() {
        return new RuleView(7L, "java", "java", null, true, 1, "Москва", null, true, null, false, null,
                "hash", "Java dev", null, RuleMode.CONFIRM, 30, 15, true, false, true, 0);
    }

    @Test
    void withoutSignatureNothingIsServed() throws Exception {
        mvc.perform(get("/api/rules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        mvc.perform(get("/api/rules").header("Authorization", "tma user=%7B%22id%22%3A42%7D&hash=00"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ruleService);
    }

    @Test
    void validSignatureOfSomeoneOutsideTheWhitelistIsForbidden() throws Exception {
        String stranger = "tma " + InitDataValidatorTest.sign(TOKEN, Instant.now(), "{\"id\":77,\"username\":\"x\"}");

        mvc.perform(get("/api/rules").header("Authorization", stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));

        verifyNoInteractions(ruleService);
    }

    @Test
    void listsRulesOfTheSignedUser() throws Exception {
        when(ruleService.list(user)).thenReturn(List.of(view()));

        mvc.perform(get("/api/rules").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].mode").value("CONFIRM"))
                .andExpect(jsonPath("$[0].ready").value(true))
                .andExpect(jsonPath("$[0].resumeTitle").value("Java dev"));
    }

    @Test
    void createsRuleFromFormJson() throws Exception {
        when(ruleService.create(eq(user), eq(new RuleData("java", "java OR kotlin", null, true, 1, "Москва", 200000, false,
                "between1And3", true, null, "hash", null, RuleMode.AUTO, 20, 30, false)))).thenReturn(view());

        mvc.perform(post("/api/rules").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"java","keywords":"java OR kotlin","titleOnly":true,"areaId":1,"areaName":"Москва",
                                 "salaryFrom":200000,"onlyWithSalary":false,"experience":"between1And3",
                                 "remoteOnly":true,"resumeHash":"hash","mode":"AUTO","dailyLimit":20,
                                 "intervalMinutes":30,"skipWithTest":false}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void validationErrorNamesTheField() throws Exception {
        when(ruleService.create(any(), any())).thenThrow(new ValidationException("dailyLimit", "Лимит - от 1 до 100."));

        mvc.perform(post("/api/rules").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"dailyLimit\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.field").value("dailyLimit"))
                .andExpect(jsonPath("$.message").value("Лимит - от 1 до 100."));
    }

    @Test
    void mapsServiceFailuresToDistinctCodes() throws Exception {
        when(ruleService.get(user, 1L)).thenThrow(new NotFoundException("Правила уже нет."));
        when(ruleService.preview(user, 2L)).thenThrow(new HhSessionExpiredException());
        when(ruleService.preview(user, 3L)).thenThrow(new HhException("boom"));

        mvc.perform(get("/api/rules/1").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(get("/api/rules/2/preview").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("hh_session_expired"));
        mvc.perform(get("/api/rules/3/preview").header("Authorization", auth))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.error").value("hh_unavailable"));
    }

    @Test
    void brokenJsonIsABadRequestNotAServerError() throws Exception {
        mvc.perform(post("/api/rules").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"));
    }
}
