package com.antonk404.hhbot.telegram.access;

import com.antonk404.hhbot.service.WhitelistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminAccess {

    private final Set<String> adminUsernames;

    public AdminAccess(@Value("${bot.admin-usernames}") String adminUsernamesProperty) {
        this.adminUsernames = Arrays.stream(adminUsernamesProperty.split(","))
                .map(WhitelistService::normalize)
                .filter(username -> !username.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String username) {
        return username != null && adminUsernames.contains(WhitelistService.normalize(username));
    }
}
