package com.antonk404.hhbot.telegram.handler.command;

final class Commands {

    private Commands() {
    }

    /** Делит сообщение по пробелам и срезает суффикс «@botname» с токена команды. */
    static String[] tokens(String text) {
        String[] parts = text.trim().split("\\s+");
        parts[0] = parts[0].split("@")[0];
        return parts;
    }

    static boolean isCommand(String text, String command) {
        return tokens(text)[0].equalsIgnoreCase(command);
    }
}
