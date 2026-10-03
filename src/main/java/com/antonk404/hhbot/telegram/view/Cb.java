package com.antonk404.hhbot.telegram.view;

/** Callback data всех кнопок. В одном месте, чтобы экран и обработчик не разошлись в написании. */
public final class Cb {

    public static final String HOME = "m:home";
    public static final String STATS = "m:stats";
    public static final String PAUSE = "m:pause";

    public static final String ACCOUNT = "acc:show";
    public static final String CONNECT = "acc:cookies";
    public static final String CONNECT_HOW = "acc:how:";
    public static final String RESUMES = "acc:resumes";
    public static final String LOGOUT = "acc:logout";

    public static final String RULES = "r:list";
    public static final String RULE_NEW = "r:new";

    public static final String LETTERS = "l:list";
    public static final String LETTER_NEW = "l:new";

    public static final String VACANCY = "v:";

    private Cb() {
    }

    /** {@code r:<id>:<действие>[:<аргумент>]} */
    public static String rule(Long id, String action) {
        return "r:" + id + ":" + action;
    }

    /** {@code l:<id>:<действие>} */
    public static String letter(Long id, String action) {
        return "l:" + id + ":" + action;
    }

    /** {@code v:<id вакансии>:<a|s|l>} - откликнуться, пропустить, показать письмо. */
    public static String vacancy(String vacancyId, String action) {
        return VACANCY + vacancyId + ":" + action;
    }
}
