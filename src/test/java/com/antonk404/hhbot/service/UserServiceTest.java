package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.BotUser;
import com.antonk404.hhbot.domain.repo.BotUserRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserServiceTest {
    private final BotUserRepository repo = mock(BotUserRepository.class);
    @Test void registersWithoutUsernameAndNeverUsesLegacyNameToClaimData() {
        when(repo.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new UserService(repo, true);
        var user = service.resolve(42L, null).orElseThrow();
        assertEquals(42L, user.getTelegramId());
        assertNull(user.getUsername());
        verify(repo, never()).findByUsername(any());
        service.resolve(77L, "legacy_name").orElseThrow();
        verify(repo, never()).findByUsername(any());
    }
    @Test void usernameChangesKeepExistingProfileAndSettings() {
        var user = new BotUser("old", "admin"); user.setTelegramId(42L); user.setPaused(true);
        when(repo.findByTelegramId(42L)).thenReturn(Optional.of(user));
        var service = new UserService(repo, true);
        assertSame(user, service.resolve(42L, "new").orElseThrow());
        assertTrue(user.isPaused()); assertEquals("new", user.getUsername());
        assertSame(user, service.resolve(42L, null).orElseThrow());
        assertNull(user.getUsername());
    }
    @Test void closedRegistrationStillAcceptsKnownTelegramIds() {
        var user = new BotUser("old", "admin"); user.setTelegramId(42L);
        when(repo.findByTelegramId(42L)).thenReturn(Optional.of(user));
        var service = new UserService(repo, false);
        assertTrue(service.resolve(77L, "old").isEmpty());
        assertSame(user, service.resolve(42L, "old").orElseThrow());
    }
    @Test void concurrentFirstRequestsCreateOneProfile() throws Exception {
        var stored = new java.util.concurrent.atomic.AtomicReference<BotUser>();
        when(repo.findByTelegramId(42L)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repo.save(any())).thenAnswer(call -> { stored.set(call.getArgument(0)); return stored.get(); });
        var service = new UserService(repo, true);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var jobs = new ArrayList<Future<BotUser>>();
            for (int i = 0; i < 30; i++) jobs.add(executor.submit(() -> service.resolve(42L, "u").orElseThrow()));
            for (var job : jobs) assertSame(jobs.getFirst().get(), job.get());
        }
        verify(repo, times(1)).save(any());
    }
}
