package com.antonk404.hhbot.service;

import com.antonk404.hhbot.domain.HhSession;
import com.antonk404.hhbot.domain.SessionState;
import com.antonk404.hhbot.hh.HhGateway;
import com.antonk404.hhbot.hh.HhProfile;
import com.antonk404.hhbot.hh.HhSessionCookies;
import com.antonk404.hhbot.hh.HhSessionExpiredException;
import com.antonk404.hhbot.repo.HhSessionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Сессия hh пользователя: подключить, достать куки, погасить. */
@Service
public class HhAccountService {

    private final HhSessionRepository hhSessionRepository;
    private final HhGateway hhGateway;
    private final CookieCipher cookieCipher;

    public HhAccountService(
            HhSessionRepository hhSessionRepository, HhGateway hhGateway, CookieCipher cookieCipher) {
        this.hhSessionRepository = hhSessionRepository;
        this.hhGateway = hhGateway;
        this.cookieCipher = cookieCipher;
    }

    /**
     * Проверяет вставленные куки на живом hh и только потом сохраняет.
     *
     * <p>Сохранить без проверки проще, но тогда о нерабочих куках человек узнал бы через час от
     * сканера, а не сразу, пока браузер с нужной вкладкой ещё открыт.
     *
     * @throws IllegalArgumentException  в тексте нет {@code hhtoken}
     * @throws HhSessionExpiredException hh эти куки не принял
     */
    public HhProfile connect(Long userId, String pasted) {
        HhSessionCookies cookies = HhSessionCookies.parse(pasted);
        HhProfile profile = hhGateway.profile(cookies);
        String encrypted = cookieCipher.encrypt(cookies.header());
        HhSession session = hhSessionRepository.findByUserId(userId)
                .map(existing -> {
                    existing.renew(encrypted, profile.ownerName());
                    return existing;
                })
                .orElseGet(() -> new HhSession(userId, encrypted, profile.ownerName()));
        hhSessionRepository.save(session);
        return profile;
    }

    public Optional<HhSession> session(Long userId) {
        return hhSessionRepository.findByUserId(userId);
    }

    /** Куки живой сессии; пусто, если сессии нет или она погашена. */
    public Optional<HhSessionCookies> cookies(Long userId) {
        return hhSessionRepository.findByUserId(userId)
                .filter(session -> session.getState() == SessionState.ACTIVE)
                .map(session -> HhSessionCookies.parse(cookieCipher.decrypt(session.getCookiesEncrypted())));
    }

    /** Свежий профиль с hh. Мёртвую сессию гасит сам, исключение всё равно летит дальше. */
    public HhProfile profile(Long userId) {
        HhSessionCookies cookies = cookies(userId).orElseThrow(HhSessionExpiredException::new);
        try {
            return hhGateway.profile(cookies);
        } catch (HhSessionExpiredException e) {
            markExpired(userId);
            throw e;
        }
    }

    /** @return {@code true}, если сессия была живой - то есть об этом ещё никто не сообщал */
    public boolean markExpired(Long userId) {
        Optional<HhSession> session = hhSessionRepository.findByUserId(userId)
                .filter(found -> found.getState() == SessionState.ACTIVE);
        session.ifPresent(found -> {
            found.expire();
            hhSessionRepository.save(found);
        });
        return session.isPresent();
    }

    /** Удаляет куки из базы. На самом hh сессия остаётся - выйти там может только сам человек. */
    public void disconnect(Long userId) {
        hhSessionRepository.findByUserId(userId).ifPresent(hhSessionRepository::delete);
    }
}
