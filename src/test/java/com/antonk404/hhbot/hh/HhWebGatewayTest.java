package com.antonk404.hhbot.hh;

import com.antonk404.hhbot.domain.dto.ApplyResult;
import com.antonk404.hhbot.domain.dto.HhArea;
import com.antonk404.hhbot.domain.dto.HhProfile;
import com.antonk404.hhbot.domain.dto.HhResume;
import com.antonk404.hhbot.domain.dto.HhVacancy;
import com.antonk404.hhbot.domain.dto.SearchQuery;
import com.antonk404.hhbot.hh.exceptions.HhException;
import com.antonk404.hhbot.hh.exceptions.HhSessionExpiredException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HhWebGatewayTest {

    private static final HhSessionCookies COOKIES = HhSessionCookies.parse("hhtoken=tok; _xsrf=xs");

    /** Отдаёт заранее заданный ответ и запоминает, о чём его спросили. */
    private static final class FakeHttp implements HhHttp {
        private final List<Response> responses;
        private final List<String> urls = new ArrayList<>();
        private Map<String, String> headers;
        private Map<String, String> form;

        /** Ответы по порядку запросов; последний повторяется. */
        private FakeHttp(Response... responses) {
            this.responses = List.of(responses);
        }

        private Response next() {
            return responses.get(Math.min(urls.size(), responses.size()) - 1);
        }

        @Override
        public Response get(String url, Map<String, String> headers) {
            this.urls.add(url);
            this.headers = headers;
            return next();
        }

        @Override
        public Response postForm(String url, Map<String, String> headers, Map<String, String> form) {
            this.urls.add(url);
            this.headers = headers;
            this.form = form;
            return next();
        }
    }

    private static HhWebGateway gateway(HhHttp http) {
        return new HhWebGateway(http, "https://hh.ru", "test-agent");
    }

    private static String resource(String name) {
        try (InputStream in = HhWebGatewayTest.class.getResourceAsStream("/hh/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HhHttp.Response ok(String body) {
        return new HhHttp.Response(200, body, null);
    }

    /** search.html - урезанная настоящая страница поиска hh, снятая 2026-10-03. */
    @Test
    void parsesVacanciesFromSearchPage() {
        FakeHttp http = new FakeHttp(ok(resource("search.html")));

        List<HhVacancy> vacancies = gateway(http).search(
                COOKIES, SearchQuery.of("java"), 0);

        assertEquals(3, vacancies.size());
        assertEquals(new HhVacancy("138056298", "Java backend developer (Kotlin)", "Максилект",
                "от 230 000 до 310 000 ₽", "Москва", "https://hh.ru/vacancy/138056298", false, false),
                vacancies.get(0));
        assertEquals("Банк ПСБ, ИТ", vacancies.get(1).company());
        assertEquals("", vacancies.get(1).salary());
        assertTrue(vacancies.get(2).hasTest());
        assertEquals("hhtoken=tok; _xsrf=xs", http.headers.get("Cookie"));
    }

    @Test
    void buildsSearchUrlFromQuery() {
        assertEquals("text=java+developer&search_field=name&area=1&salary=200000&only_with_salary=true"
                        + "&experience=between1And3&work_format=REMOTE&order_by=publication_time&page=2",
                HhWebGateway.searchParams(new SearchQuery("java developer", true, null, 1, 200000, true,
                        "between1And3", List.of("REMOTE"), List.of(), List.of(), null), 2));
        assertEquals("text=java&order_by=publication_time&page=0",
                HhWebGateway.searchParams(new SearchQuery("java", false, null, null, null, true, null,
                        List.of(), List.of(), List.of(), null), 0));
    }

    /** Порог без «только с зарплатой»: hh покажет и вакансии, где зарплата не указана. */
    @Test
    void salaryThresholdCanKeepVacanciesWithoutSalary() {
        assertEquals("text=java&salary=200000&order_by=publication_time&page=0",
                HhWebGateway.searchParams(new SearchQuery("java", false, null, null, 200000, false, null,
                        List.of(), List.of(), List.of(), null), 0));
    }

    /**
     * Должность - в названии, стек - в описании. Синтаксис проверен на живом hh 2026-10-03:
     * такой запрос сузил выдачу по «devops» с 297 до 251 вакансии.
     */
    @Test
    void skillsAreSearchedInDescriptionAndKeywordsInTitle() {
        assertEquals("text=NAME%3A%28devops+OR+sre%29+AND+DESCRIPTION%3A%28kubernetes+OR+k8s%29"
                        + "&order_by=publication_time&page=0",
                HhWebGateway.searchParams(new SearchQuery("devops OR sre", true, "kubernetes OR k8s", null, null,
                        true, null, List.of(), List.of(), List.of(), null), 0));
        assertEquals("text=%28devops%29+AND+DESCRIPTION%3A%28k8s%29&order_by=publication_time&page=0",
                HhWebGateway.searchParams(new SearchQuery("devops", false, "k8s", null, null,
                        true, null, List.of(), List.of(), List.of(), null), 0));
    }

    @Test
    void multiValueFiltersRepeatTheParameter() {
        assertEquals("text=devops&search_field=name&work_format=REMOTE&work_format=HYBRID&employment_form=FULL"
                        + "&label=not_from_agency&label=accredited_it&search_period=7&order_by=publication_time&page=0",
                HhWebGateway.searchParams(new SearchQuery("devops", true, " ", null, null, true, null,
                        List.of("REMOTE", "HYBRID"), List.of("FULL"), List.of("not_from_agency", "accredited_it"), 7), 0));
    }

    /** Ответ - настоящий, снят с hh 2026-10-03 запросом «каз». */
    @Test
    void parsesAreaSuggest() {
        FakeHttp http = new FakeHttp(ok("{\"items\":[{\"id\":88,\"text\":\"Казань\",\"type\":5,\"parent\":"
                + "{\"id\":1624,\"text\":\"Республика Татарстан\",\"parent\":{\"id\":113,\"text\":\"Россия\"}}},"
                + "{\"id\":40,\"text\":\"Казахстан\",\"type\":1}]}"));

        List<HhArea> areas = gateway(http).areas("каз");

        assertEquals(List.of(new HhArea(88, "Казань", "Республика Татарстан"), new HhArea(40, "Казахстан", "")), areas);
        assertEquals("https://hh.ru/autosuggest/multiprefix/v2?d=areas_RU&q=%D0%BA%D0%B0%D0%B7", http.urls.getFirst());
        assertFalse(http.headers.containsKey("Cookie"));
    }

    @Test
    void pageWithoutStateIsAnErrorNotAnEmptyResult() {
        FakeHttp http = new FakeHttp(ok("<html>проверка браузера</html>"));

        assertThrows(HhException.class, () -> gateway(http).search(
                COOKIES, SearchQuery.of("java"), 0));
    }

    @Test
    void forbiddenSearchIsNotAnExpiredSession() {
        FakeHttp http = new FakeHttp(new HhHttp.Response(403, "", null));

        HhException e = assertThrows(HhException.class, () -> gateway(http).search(
                COOKIES, SearchQuery.of("java"), 0));
        assertFalse(e instanceof HhSessionExpiredException);
    }

    @Test
    void profileRedirectedToLoginMeansExpiredSession() {
        FakeHttp http = new FakeHttp(new HhHttp.Response(302, "", "/account/login?backurl=%2Fapplicant%2Fresumes"));

        assertThrows(HhSessionExpiredException.class, () -> gateway(http).profile(COOKIES));
    }

    /** hh редиректит и живых пользователей - со старого адреса страницы на новый. Это не мёртвая сессия. */
    @Test
    void followsRedirectsInsideHh() {
        FakeHttp http = new FakeHttp(
                new HhHttp.Response(302, "", "/applicant/my_resumes?from=old"),
                ok("<template id=\"HH-Lux-InitialState\">{&#34;account&#34;:{&#34;firstName&#34;:&#34;А&#34;,"
                        + "&#34;email&#34;:&#34;a@b&#34;},&#34;applicantResumes&#34;:[]}</template>"));

        HhProfile profile = gateway(http).profile(COOKIES);

        assertEquals("А", profile.ownerName());
        assertEquals(List.of("https://hh.ru/applicant/my_resumes", "https://hh.ru/applicant/my_resumes?from=old"), http.urls);;
    }

    @Test
    void redirectAfterRedirectToLoginIsStillAnExpiredSession() {
        FakeHttp http = new FakeHttp(
                new HhHttp.Response(301, "", "https://moscow.hh.ru/applicant/resumes"),
                new HhHttp.Response(302, "", "/account/login?backurl=x"));

        assertThrows(HhSessionExpiredException.class, () -> gateway(http).profile(COOKIES));
    }

    /** Куки сессии не должны уехать на чужой домен вслед за редиректом. */
    @Test
    void doesNotFollowRedirectsOffSite() {
        FakeHttp http = new FakeHttp(new HhHttp.Response(302, "", "https://evil.example/hh.ru"));

        assertThrows(HhException.class, () -> gateway(http).profile(COOKIES));
        assertEquals(1, http.urls.size());
    }

    @Test
    void redirectLoopEndsWithAnError() {
        FakeHttp http = new FakeHttp(new HhHttp.Response(302, "", "/applicant/resumes"));

        assertThrows(HhException.class, () -> gateway(http).profile(COOKIES));
        assertEquals(5, http.urls.size());
    }

    @Test
    void profileReadsNameAndResumes() {
        String state = "{\"account\":{\"firstName\":\"Антон\",\"lastName\":\"К\",\"email\":\"a@b\"},"
                + "\"applicantResumes\":[{\"_attributes\":{\"hash\":\"abc123\"},\"title\":[{\"string\":\"Java dev\"}]},"
                + "{\"_attributes\":{\"hash\":\"def456\"},\"title\":[]}]}";
        FakeHttp http = new FakeHttp(ok("<template id=\"HH-Lux-InitialState\">"
                + state.replace("\"", "&#34;") + "</template>"));

        HhProfile profile = gateway(http).profile(COOKIES);

        assertEquals("Антон К", profile.ownerName());
        assertEquals(List.of(new HhResume("abc123", "Java dev"), new HhResume("def456", "Без названия")),
                profile.resumes());
    }

    @Test
    void guestStateOnProfilePageMeansExpiredSession() {
        String state = "{\"account\":{\"firstName\":null,\"lastName\":null,\"email\":null}}";
        FakeHttp http = new FakeHttp(ok("<template id=\"HH-Lux-InitialState\">"
                + state.replace("\"", "&#34;") + "</template>"));

        assertThrows(HhSessionExpiredException.class, () -> gateway(http).profile(COOKIES));
    }

    @Test
    void applySendsXsrfInBothHeaderAndForm() {
        FakeHttp http = new FakeHttp(ok("{\"success\":true}"));

        ApplyResult result = gateway(http).apply(COOKIES, "42", "resumehash", "Здравствуйте");

        assertInstanceOf(ApplyResult.Sent.class, result);
        assertEquals("https://hh.ru/applicant/vacancy_response/popup", http.urls.getFirst());
        assertEquals("xs", http.headers.get("X-Xsrftoken"));
        assertEquals("xs", http.form.get("_xsrf"));
        assertEquals("42", http.form.get("vacancy_id"));
        assertEquals("resumehash", http.form.get("resume_hash"));
        assertEquals("Здравствуйте", http.form.get("letter"));
    }

    @Test
    void mapsApplyRefusals() {
        assertInstanceOf(ApplyResult.LimitReached.class, applied(400, "{\"error\":\"negotiations-limit-exceeded\"}"));
        assertInstanceOf(ApplyResult.NeedsTest.class, applied(400, "{\"error\":\"test-required\"}"));
        assertInstanceOf(ApplyResult.AlreadyApplied.class, applied(400, "{\"error\":\"alreadyApplied\"}"));
        assertInstanceOf(ApplyResult.Captcha.class, applied(200, "{\"hhcaptcha\":{\"isBot\":true}}"));
        assertInstanceOf(ApplyResult.Captcha.class, applied(403, "<html>nope</html>"));
        assertInstanceOf(ApplyResult.Failed.class, applied(500, "oops"));
        assertInstanceOf(ApplyResult.Failed.class, applied(200, "{\"error\":\"unknown\"}"));
        assertInstanceOf(ApplyResult.SessionExpired.class,
                HhWebGateway.applyResult(new HhHttp.Response(302, "", "https://hh.ru/account/login"), "1"));
    }

    private static ApplyResult applied(int status, String body) {
        return HhWebGateway.applyResult(new HhHttp.Response(status, body, null), "1");
    }

    @Test
    void unescapesEntitiesInOnePass() {
        assertEquals("{\"a\":\"&#34; <b>\"}", InitialState.unescape("{&#34;a&#34;:&#34;&amp;#34; &lt;b&gt;&#x22;}"));
    }
}
