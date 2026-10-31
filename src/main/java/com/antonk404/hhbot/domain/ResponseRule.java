package com.antonk404.hhbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.List;

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

    /** Форматы работы, как их называет поиск hh. */
    public static final List<String> WORK_FORMATS = List.of("REMOTE", "HYBRID", "ON_SITE", "FIELD_WORK");

    public static final List<String> EMPLOYMENT_FORMS = List.of("FULL", "PART", "PROJECT", "FLY_IN_FLY_OUT");

    /** Метки-фильтры hh: без агентств, аккредитованные ИТ-компании, меньше 10 откликов, стажировка. */
    public static final List<String> LABELS = List.of("not_from_agency", "accredited_it", "low_performance", "internship");

    /** За сколько дней брать вакансии. */
    public static final List<Integer> PERIODS_DAYS = List.of(1, 3, 7, 30);

    /** Коды опыта, которые понимает поиск hh, в порядке самого hh. */
    public static final List<String> EXPERIENCE_CODES =
            List.of("noExperience", "between1And3", "between3And6", "moreThan6");

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

    /**
     * Что должно быть в описании вакансии - тем же языком запросов: {@code kubernetes OR k8s}.
     * Отдельно от ключевых слов, потому что ищется в другом месте: должность - в названии,
     * стек - в тексте.
     */
    @Column(length = 512)
    private String skills;

    /** Через запятую. Вакансия с любым из этих слов в названии отбрасывается. */
    @Column(name = "minus_words", length = 512)
    private String minusWords;

    /** Искать только в названии вакансии. Иначе hh находит слово и в описании - мусора в разы больше. */
    @Column(name = "title_only", nullable = false)
    private boolean titleOnly = true;

    /** Id региона hh (1 - Москва, 2 - Петербург, 113 - Россия); null - без ограничения. */
    @Column(name = "area_id")
    private Integer areaId;

    /** Название региона на момент выбора - чтобы показать правило без похода в hh. */
    @Column(name = "area_name", length = 128)
    private String areaName;

    @Column(name = "salary_from")
    private Integer salaryFrom;

    /**
     * Не показывать вакансии без указанной зарплаты. Имеет смысл только вместе с порогом: без
     * него hh подмешивает вакансии без вилки, и порог перестаёт что-либо отсекать.
     */
    // ColumnDefault - для уже существующих строк: без него Hibernate не сможет добавить
    // NOT NULL колонку в таблицу, где есть правила.
    @ColumnDefault("true")
    @Column(name = "only_with_salary", nullable = false)
    private boolean onlyWithSalary = true;

    /**
     * Осталась от времён, когда опыт был одним кодом: в varchar(16) несколько кодов не влезут.
     * Читается, пока правило не пересохранили; смысл теперь несёт {@link #experienceCodes}.
     */
    @Column(length = 16)
    private String experience;

    /** Коды из {@link #EXPERIENCE_CODES} через запятую; пусто - любой опыт. */
    @Column(name = "experience_codes", length = 64)
    private String experienceCodes;

    /**
     * Осталась от времён, когда формат работы был одним флажком. Колонка NOT NULL, поэтому поле
     * приходится держать и писать; смысл теперь несёт {@link #workFormats}.
     */
    @Column(name = "remote_only", nullable = false)
    private boolean remoteOnly = false;

    /** Коды из {@link #WORK_FORMATS} через запятую; пусто - любой формат. */
    @Column(name = "work_formats", length = 64)
    private String workFormats;

    /** Коды из {@link #EMPLOYMENT_FORMS} через запятую; пусто - любая занятость. */
    @Column(name = "employment_forms", length = 64)
    private String employmentForms;

    /** Коды из {@link #LABELS} через запятую. */
    @Column(length = 128)
    private String labels;

    /** Не старше стольких дней; null - без ограничения. */
    @Column(name = "period_days")
    private Integer periodDays;

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
    private int legacyDailyLimit = 30;

    @ColumnDefault("15")
    @Column(name = "interval_minutes", nullable = false)
    private int legacyIntervalMinutes = 15;

    /**
     * Молча пропускать вакансии с тестом работодателя. Выключено - бот присылает их ссылкой,
     * чтобы человек откликнулся сам: автоматически на такие не откликнуться в любом случае.
     */
    @ColumnDefault("true")
    @Column(name = "skip_with_test", nullable = false)
    private boolean skipWithTest = true;

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

    public String getAreaName() {
        return areaName;
    }

    /** Регион задаётся парой: id для запроса и название для человека. null - без ограничения. */
    public void setArea(Integer areaId, String areaName) {
        this.areaId = areaId;
        this.areaName = areaId == null ? null : areaName;
    }

    public boolean isOnlyWithSalary() {
        return onlyWithSalary;
    }

    public void setOnlyWithSalary(boolean onlyWithSalary) {
        this.onlyWithSalary = onlyWithSalary;
    }


    public boolean isSkipWithTest() {
        return skipWithTest;
    }

    public void setSkipWithTest(boolean skipWithTest) {
        this.skipWithTest = skipWithTest;
    }

    public Integer getSalaryFrom() {
        return salaryFrom;
    }

    public void setSalaryFrom(Integer salaryFrom) {
        this.salaryFrom = salaryFrom;
    }

    /** Правила, созданные до появления нескольких диапазонов, читаются по старой колонке. */
    public List<String> getExperiences() {
        if (experienceCodes == null && experience != null) {
            return List.of(experience);
        }
        return split(experienceCodes);
    }

    public void setExperiences(List<String> codes) {
        this.experienceCodes = String.join(",", codes);
        // Старая колонка остаётся осмысленной, пока выбран ровно один диапазон.
        this.experience = codes.size() == 1 ? codes.getFirst() : null;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    /** Правила, созданные до появления списка форматов, читаются по старому флажку. */
    public List<String> getWorkFormats() {
        if (workFormats == null && remoteOnly) {
            return List.of("REMOTE");
        }
        return split(workFormats);
    }

    public void setWorkFormats(List<String> formats) {
        this.workFormats = String.join(",", formats);
        this.remoteOnly = formats.equals(List.of("REMOTE"));
    }

    public boolean isRemoteOnly() {
        return getWorkFormats().equals(List.of("REMOTE"));
    }

    public void setRemoteOnly(boolean remoteOnly) {
        setWorkFormats(remoteOnly ? List.of("REMOTE") : List.of());
    }

    public List<String> getEmploymentForms() {
        return split(employmentForms);
    }

    public void setEmploymentForms(List<String> forms) {
        this.employmentForms = String.join(",", forms);
    }

    public List<String> getLabels() {
        return split(labels);
    }

    public void setLabels(List<String> labels) {
        this.labels = String.join(",", labels);
    }

    public Integer getPeriodDays() {
        return periodDays;
    }

    public void setPeriodDays(Integer periodDays) {
        this.periodDays = periodDays;
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
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
