package com.antonk404.hhbot.hh;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JdkHhHttp implements HhHttp {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Response get(String url, Map<String, String> headers) {
        return send(request(url, headers).GET().build());
    }

    @Override
    public Response postForm(String url, Map<String, String> headers, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return send(request(url, headers)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    private static HttpRequest.Builder request(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT);
        headers.forEach(builder::header);
        return builder;
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body(),
                    response.headers().firstValue("Location").orElse(null));
        } catch (IOException e) {
            // Только путь: в строке запроса поиска нет секретов, но привычка не писать url целиком
            // дешевле, чем разбираться потом, что в нём оказалось.
            throw new HhException("hh request failed: " + request.uri().getPath(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HhException("hh request interrupted: " + request.uri().getPath(), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
