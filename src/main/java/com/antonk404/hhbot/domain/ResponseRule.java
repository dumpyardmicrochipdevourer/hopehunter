package com.antonk404.hhbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;

/**
 * Правило отклика: что искать, что отсеять, каким резюме и с каким письмом откликаться.
 *
 * <p>Новое правило выключено и стоит в режиме {@code CONFIRM}. Правило, которое начинает
 * рассылать отклики в ту секунду, когда ему ввели ключевые слова, - это способ разослать резюме
 * не туда раньше, чем человек успел настроить минус-слова.
 */
@Entity
@Table(name = "response_rule")
public class ResponseRule {

    public static final int DEFAULT_DAILY_LIMIT = 30;
    public static final int MAX_DAILY_LIMIT = 100;

    /** Коды опыта, которые понимает поиск hh. */
    public static final Set<String> EXPERIENCE_CODES =
            Set.of("noExperience", "between1And3", "between3And6", "moreThan6");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String name;

    /** Текст запроса как в строке поиска hh; язык запросов hh (OR, NOT, кавычки) работает. */
    @Column(length = 512)
    private String keywords;

    /** Через запятую. Вакансия с любым из этих слов в названии отбрасывается. */
    @Column(name = "minus_words", length = 512)
    private String minusWords;

    /** Искать только в названии вакансии. Иначе hh находит слово и в описании - мусора в разы больше. */
    @Column(name = "title_only", nullable = false)
    private boolean titleOnly = true;

    /** Id региона hh (1 - Москва, 2 - Петербург, 113 - Россия); null - без ограничения. */
    @Column(name = "area_id")
    private Integer areaId;

    @Column(name = "salary_from")
    private Integer salaryFrom;

    /** Код опыта hh: noExperience, between1And3, between3And6, moreThan6; null - любой. */
    @Column(length = 16)
    private String experience;

    @Column(name = "remote_only", nullable = false)
    private boolean remoteOnly = false;

    /** Через запятую, подстроки названий компаний. */
    @Column(name = "company_blacklist", length = 1024)
    private String companyBlacklist;

    /** Хеш резюме на hh - им оно адресуется в запросе отклика. */
    @Column(name = "resume_hash", length = 64)
    private String resumeHash;

    /** Название резюме на момент выбора, чтобы показать правило без похода в hh. */
    @Column(name = "resume_title", length = 256)
    private String resumeTitle;

    @Column(name = "letter_template_id")
    private Long letterTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RuleMode mode = RuleMode.CONFIRM;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit = DEFAULT_DAILY_LIMIT;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ResponseRule() {
    }

    public ResponseRule(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    /** Без ключевых слов и резюме откликаться нечем и не на что. */
    public boolean isReady() {
        return keywords != null && !keywords.isBlank() && resumeHash != null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getMinusWords() {
        return minusWords;
    }

    public void setMinusWords(String minusWords) {
        this.minusWords = minusWords;
    }

    public boolean isTitleOnly() {
        return titleOnly;
    }

    public void setTitleOnly(boolean titleOnly) {
        this.titleOnly = titleOnly;
    }

    public Integer getAreaId() {
        return areaId;
    }

    public void setAreaId(Integer areaId) {
        this.areaId = areaId;
    }

    public Integer getSalaryFrom() {
        return salaryFrom;
    }

    public void setSalaryFrom(Integer salaryFrom) {
        this.salaryFrom = salaryFrom;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public boolean isRemoteOnly() {
        return remoteOnly;
    }

    public void setRemoteOnly(boolean remoteOnly) {
        this.remoteOnly = remoteOnly;
    }

    public String getCompanyBlacklist() {
        return companyBlacklist;
    }

    public void setCompanyBlacklist(String companyBlacklist) {
        this.companyBlacklist = companyBlacklist;
    }

    public String getResumeHash() {
        return resumeHash;
    }

    public String getResumeTitle() {
        return resumeTitle;
    }

    public void setResume(String resumeHash, String resumeTitle) {
        this.resumeHash = resumeHash;
        this.resumeTitle = resumeTitle;
    }

    public Long getLetterTemplateId() {
        return letterTemplateId;
    }

    public void setLetterTemplateId(Long letterTemplateId) {
        this.letterTemplateId = letterTemplateId;
    }

    public RuleMode getMode() {
        return mode;
    }

    public void setMode(RuleMode mode) {
        this.mode = mode;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
