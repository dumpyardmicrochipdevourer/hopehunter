package com.antonk404.hhbot.telegram;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/** Один экран меню: текст и кнопки под ним. */
public record Screen(String text, InlineKeyboardMarkup keyboard) {
}
