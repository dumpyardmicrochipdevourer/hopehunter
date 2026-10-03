package com.antonk404.hhbot.hh;

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
        private final Response response;
        private final List<String> urls = new ArrayList<>();
        private Map<String, String> headers;
        private Map<String, String> form;

        private FakeHttp(Response response) {
            this.response = response;
        }

        @Override
        public Response get(String url, Map<String, String> headers) {
            this.urls.add(url);
            this.headers = headers;
            return response;
        }

        @Override
        public Response postForm(String url, Map<String, String> headers, Map<String, String> form) {
            this.urls.add(url);
            this.headers = headers;
            this.form = form;
            return response;
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
                COOKIES, new SearchQuery("java", true, 1, null, null, false), 0);

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
                HhWebGateway.searchParams(
                        new SearchQuery("java developer", true, 1, 200000, "between1And3", true), 2));
        assertEquals("text=java&order_by=publication_time&page=0",
                HhWebGateway.searchParams(new SearchQuery("java", false, null, null, null, false), 0));
    }

    @Test
    void pageWithoutStateIsAnErrorNotAnEmptyResult() {
        FakeHttp http = new FakeHttp(ok("<html>проверка браузера</html>"));

        assertThrows(HhException.class, () -> gateway(http).search(
                COOKIES, new SearchQuery("java", true, null, null, null, false), 0));
    }

    @Test
    void forbiddenSearchIsNotAnExpiredSession() {
        FakeHttp http = new FakeHttp(new HhHttp.Response(403, "", null));

        HhException e = assertThrows(HhException.class, () -> gateway(http).search(
                COOKIES, new SearchQuery("java", true, null, null, null, false), 0));
        assertFalse(e instanceof HhSessionExpiredException);
    }

    @Test
    void profileRedirectedToLoginMeansExpiredSession() {
        FakeHttp http = new FakeHttp(new HhHttp.Response(302, "", "/account/login?backurl=%2Fapplicant%2Fresumes"));

        assertThrows(HhSessionExpiredException.class, () -> gateway(http).profile(COOKIES));
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
