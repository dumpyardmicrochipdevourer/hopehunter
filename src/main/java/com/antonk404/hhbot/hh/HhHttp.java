package com.antonk404.hhbot.hh;

import java.util.Map;

/**
 * Транспорт до hh. Отдельно от {@link HhWebGateway}, чтобы разбор ответов проверялся на
 * сохранённых страницах, без сети.
 *
 * <p>Редиректам не следует: редирект на страницу входа - это и есть признак мёртвой сессии, и
 * после автоматического перехода он выглядел бы как обычный ответ 200.
 */
public interface HhHttp {

    /** @param location заголовок Location или null */
    record Response(int status, String body, String location) {
    }

    Response get(String url, Map<String, String> headers);

    Response postForm(String url, Map<String, String> headers, Map<String, String> form);
}
