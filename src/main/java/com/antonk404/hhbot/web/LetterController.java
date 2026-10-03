package com.antonk404.hhbot.web;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.dto.LetterData;
import com.antonk404.hhbot.domain.dto.LetterPreview;
import com.antonk404.hhbot.domain.dto.LetterView;
import com.antonk404.hhbot.service.LetterService;
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
@RequestMapping("/api/letters")
public class LetterController {

    public record PreviewRequest(String body) {
    }

    private final LetterService letterService;

    public LetterController(LetterService letterService) {
        this.letterService = letterService;
    }

    @GetMapping
    public List<LetterView> list(@RequestAttribute(AuthInterceptor.USER) BotUser user) {
        return letterService.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LetterView create(@RequestAttribute(AuthInterceptor.USER) BotUser user, @RequestBody LetterData data) {
        return letterService.create(user, data);
    }

    @PutMapping("/{id}")
    public LetterView update(
            @RequestAttribute(AuthInterceptor.USER) BotUser user, @PathVariable Long id, @RequestBody LetterData data) {
        return letterService.update(user, id, data);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER) BotUser user, @PathVariable Long id) {
        letterService.delete(user, id);
    }

    /** Предпросмотр несохранённого текста - форма зовёт его, пока человек пишет. */
    @PostMapping("/preview")
    public LetterPreview preview(@RequestAttribute(AuthInterceptor.USER) BotUser user, @RequestBody PreviewRequest request) {
        return letterService.preview(user, request.body());
    }
}
