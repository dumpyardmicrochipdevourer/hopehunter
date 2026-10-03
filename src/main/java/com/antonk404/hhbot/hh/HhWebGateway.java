package com.antonk404.hhbot.hh;

import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhArea;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.dto.SearchQuery;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * hh.ru глазами браузера: те же страницы и тот же запрос отклика, что шлёт сайт.
 *
 * <p>Поиск проверен на живом сайте (страница и её JSON, параметры запроса). Список резюме и
 * отклик требуют залогиненной сессии и написаны по известному поведению сайта - их разбор
 * нарочно терпим к отсутствующим полям и громко падает, а не молча возвращает пустоту.
 */
@Component
public class HhWebGateway implements HhGateway {

    private static final Logger logger = LoggerFactory.getLogger(HhWebGateway.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_REDIRECTS = 4;

    private final HhHttp http;
    private final String baseUrl;
    private final String userAgent;

    public HhWebGateway(
            HhHttp http,
            @Value("${hh.base-url}") String baseUrl,
            @Value("${hh.user-agent}") String userAgent) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
    }

    @Override
    public HhProfile profile(HhSessionCookies cookies) {
        HhHttp.Response response = getPage(baseUrl + "/applicant/resumes", headers(cookies));
        JsonNode state = InitialState.parse(pageOrThrow(response, true));

        List<HhResume> resumes = new ArrayList<>();
        for (JsonNode resume : state.path("applicantResumes")) {
            String hash = resume.path("_attributes").path("hash").asText("");
            if (hash.isEmpty()) {
                continue;
            }
            String title = resume.path("title").path(0).path("string").asText("");
            resumes.add(new HhResume(hash, title.isBlank() ? "Без названия" : title));
        }

        JsonNode account = state.path("account");
        // У гостя hh отдаёт тот же блок с null-ами. Страница резюме гостю не положена, но если
        // сайт когда-нибудь начнёт отдавать её без редиректа - это всё ещё мёртвая сессия.
        if (resumes.isEmpty() && account.path("firstName").isNull() && account.path("email").isNull()) {
            throw new HhSessionExpiredException();
        }
        if (resumes.isEmpty()) {
            // Залогинен, а резюме не нашлось: либо их правда нет, либо сайт переименовал поле.
            // Имена полей (без значений) в логе отличают одно от другого без повторного захода.
            List<String> candidates = new ArrayList<>();
            state.fieldNames().forEachRemaining(field -> {
                if (field.toLowerCase(Locale.ROOT).contains("resume")) {
                    candidates.add(field);
                }
            });
            logger.warn("no resumes parsed from hh profile page; resume-like state fields: {}", candidates);
        }
        String name = (account.path("firstName").asText("") + " " + account.path("lastName").asText("")).strip();
        return new HhProfile(name, resumes);
    }

    @Override
    public List<HhVacancy> search(HhSessionCookies cookies, SearchQuery query, int page) {
        HhHttp.Response response = getPage(baseUrl + "/search/vacancy?" + searchParams(query, page), headers(cookies));
        JsonNode result = InitialState.parse(pageOrThrow(response, false)).path("vacancySearchResult");
        if (result.isMissingNode()) {
            throw new HhException("hh search page has no vacancySearchResult");
        }
        List<HhVacancy> vacancies = new ArrayList<>();
        for (JsonNode node : result.path("vacancies")) {
            String id = node.path("vacancyId").asText("");
            if (id.isEmpty()) {
                continue;
            }
            JsonNode company = node.path("company");
            vacancies.add(new HhVacancy(
                    id,
                    node.path("name").asText(""),
                    company.path("visibleName").asText(company.path("name").asText("")),
                    salary(node.path("compensation")),
                    node.path("area").path("name").asText(""),
                    node.path("links").path("desktop").asText(baseUrl + "/vacancy/" + id),
                    node.path("userTestPresent").asBoolean(false),
                    node.path("@responseLetterRequired").asBoolean(false)));
        }
        return vacancies;
    }

    static String searchParams(SearchQuery query, int page) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("text", query.text());
        if (query.titleOnly()) {
            params.put("search_field", "name");
        }
        if (query.areaId() != null) {
            params.put("area", String.valueOf(query.areaId()));
        }
        if (query.salaryFrom() != null) {
            params.put("salary", String.valueOf(query.salaryFrom()));
            // Без этого hh подмешивает вакансии без вилки - порог зарплаты перестаёт что-либо значить.
            if (query.onlyWithSalary()) {
                params.put("only_with_salary", "true");
            }
        }
        if (query.experience() != null) {
            params.put("experience", query.experience());
        }
        if (query.remoteOnly()) {
            params.put("work_format", "REMOTE");
        }
        // По умолчанию hh сортирует по своей «релевантности», и свежие вакансии тонут на дальних
        // страницах. Сканеру нужны именно новые - он читает только начало выдачи.
        params.put("order_by", "publication_time");
        params.put("page", String.valueOf(page));

        StringBuilder out = new StringBuilder();
        params.forEach((key, value) -> out.append(out.isEmpty() ? "" : "&")
                .append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return out.toString();
    }

    static String salary(JsonNode compensation) {
        boolean hasFrom = compensation.hasNonNull("from");
        boolean hasTo = compensation.hasNonNull("to");
        if (!hasFrom && !hasTo) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (hasFrom) {
            out.append("от ").append(money(compensation.get("from").asLong()));
        }
        if (hasTo) {
            out.append(out.isEmpty() ? "" : " ").append("до ").append(money(compensation.get("to").asLong()));
        }
        String currency = compensation.path("currencyCode").asText("");
        return out.append(' ').append("RUR".equals(currency) ? "₽" : currency).toString().strip();
    }

    private static String money(long amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,###", symbols).format(amount);
    }

    @Override
    public List<HhArea> areas(String text) {
        HhHttp.Response response = http.get(
                baseUrl + "/autosuggest/multiprefix/v2?d=areas_RU&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8),
                Map.of("User-Agent", userAgent, "Accept-Language", "ru-RU,ru;q=0.9"));
        if (response.status() != 200) {
            throw new HhException("hh answered " + response.status());
        }
        List<HhArea> areas = new ArrayList<>();
        try {
            for (JsonNode item : MAPPER.readTree(response.body()).path("items")) {
                if (item.hasNonNull("id") && item.hasNonNull("text")) {
                    areas.add(new HhArea(item.get("id").asInt(), item.get("text").asText(),
                            item.path("parent").path("text").asText("")));
                }
            }
        } catch (JsonProcessingException e) {
            throw new HhException("hh area suggest is not json", e);
        }
        return areas;
    }

    @Override
    public ApplyResult apply(HhSessionCookies cookies, String vacancyId, String resumeHash, String letter) {
        Map<String, String> headers = headers(cookies);
        headers.put("X-Xsrftoken", cookies.xsrf());
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Referer", baseUrl + "/vacancy/" + vacancyId);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("_xsrf", cookies.xsrf());
        form.put("vacancy_id", vacancyId);
        form.put("resume_hash", resumeHash);
        form.put("ignore_postponed", "true");
        form.put("incomplete", "false");
        form.put("letter", letter == null ? "" : letter);
        form.put("lux", "true");
        form.put("withoutTest", "no");

        HhHttp.Response response;
        try {
            response = http.postForm(baseUrl + "/applicant/vacancy_response/popup", headers, form);
        } catch (HhException e) {
            logger.warn("apply to vacancy {} failed in transport", vacancyId, e);
            return new ApplyResult.Failed("hh не ответил");
        }
        return applyResult(response, vacancyId);
    }

    /**
     * Сначала содержимое, потом статус: отказы hh приходят с разными кодами, а причина всегда в
     * теле. Ошибка, которую бот не узнал, остаётся в логе дословно - по ней этот разбор и дополняется.
     */
    static ApplyResult applyResult(HhHttp.Response response, String vacancyId) {
        if (redirectsToLogin(response) || response.status() == 401) {
            return new ApplyResult.SessionExpired();
        }
        String body = response.body() == null ? "" : response.body();
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.contains("captcha")) {
            return new ApplyResult.Captcha();
        }
        if (lower.contains("limit-exceeded") || lower.contains("limit_exceeded")) {
            return new ApplyResult.LimitReached();
        }
        if (lower.contains("test-required") || lower.contains("test_required")) {
            return new ApplyResult.NeedsTest();
        }
        if (lower.contains("already")) {
            return new ApplyResult.AlreadyApplied();
        }
        if (response.status() == 200 && !lower.contains("\"error\"")) {
            return new ApplyResult.Sent();
        }
        if (response.status() == 403) {
            // 403 без узнаваемой причины hh отдаёт и мёртвой сессии, и антиботу. Считаем худшим.
            logger.warn("apply to vacancy {}: 403 with unrecognised body: {}", vacancyId, clip(body));
            return new ApplyResult.Captcha();
        }
        logger.warn("apply to vacancy {}: status {} body {}", vacancyId, response.status(), clip(body));
        return new ApplyResult.Failed("hh ответил " + response.status());
    }

    /**
     * GET со следованием редиректам внутри hh.
     *
     * <p>Транспорт редиректам не следует нарочно: редирект на страницу входа - признак мёртвой
     * сессии, и его нужно увидеть. Но сам hh редиректит и живых пользователей - со старого адреса
     * страницы на новый, на региональный поддомен. Такие переходы проходим сами, а на входе
     * останавливаемся и отдаём ответ как есть.
     */
    private HhHttp.Response getPage(String url, Map<String, String> headers) {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HhHttp.Response response = http.get(current, headers);
            boolean redirect = response.status() >= 300 && response.status() < 400 && response.location() != null;
            if (!redirect || redirectsToLogin(response)) {
                return response;
            }
            URI next = URI.create(current).resolve(response.location());
            String host = next.getHost() == null ? "" : next.getHost();
            // Куки сессии уходят только на hh: редирект наружу - не то место, куда их нести.
            if (!host.equals("hh.ru") && !host.endsWith(".hh.ru")) {
                throw new HhException("hh redirected off-site to " + host);
            }
            logger.info("hh redirect: {} -> {}", URI.create(current).getPath(), next.getPath());
            current = next.toString();
        }
        throw new HhException("hh redirect loop at " + URI.create(current).getPath());
    }

    private Map<String, String> headers(HhSessionCookies cookies) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept-Language", "ru-RU,ru;q=0.9");
        headers.put("Cookie", cookies.header());
        return headers;
    }

    /**
     * @param personal страница доступна только залогиненному. Поиск открыт и гостю, поэтому 403
     *                 на нём - это антибот, а не мёртвая сессия, и гасить сессию из-за него нельзя.
     */
    private static String pageOrThrow(HhHttp.Response response, boolean personal) {
        if (redirectsToLogin(response) || response.status() == 401
                || (personal && response.status() == 403)) {
            throw new HhSessionExpiredException();
        }
        if (response.status() != 200) {
            throw new HhException("hh answered " + response.status());
        }
        return response.body();
    }

    private static boolean redirectsToLogin(HhHttp.Response response) {
        return response.status() >= 300 && response.status() < 400
                && response.location() != null && response.location().contains("/account/login");
    }

    private static String clip(String body) {
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
