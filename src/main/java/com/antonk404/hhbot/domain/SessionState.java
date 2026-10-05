package com.antonk404.hhbot.domain;

public enum SessionState {
    ACTIVE,
    /** hh перестал принимать куки. Сканер пользователя стоит, пока тот не войдёт заново. */
    EXPIRED
}
