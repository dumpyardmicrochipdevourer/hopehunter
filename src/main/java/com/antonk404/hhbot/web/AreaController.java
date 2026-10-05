package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.dto.HhArea;
import com.antonk404.hhbot.hh.HhGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Поиск города для формы правила: человек пишет «каз», получает Казань с её номером региона. */
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    /** Подсказка hh с одной буквы возвращает полстраны - ждём хотя бы две. */
    private static final int MIN_QUERY = 2;
    private static final int MAX_QUERY = 64;
    private static final int MAX_RESULTS = 10;

    private final HhGateway hhGateway;

    public AreaController(HhGateway hhGateway) {
        this.hhGateway = hhGateway;
    }

    @GetMapping
    public List<HhArea> search(@RequestParam("q") String query) {
        String text = query.strip();
        if (text.length() < MIN_QUERY || text.length() > MAX_QUERY) {
            return List.of();
        }
        return hhGateway.areas(text).stream().limit(MAX_RESULTS).toList();
    }
}
