package com.smart.exam.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "smart-exam.exam.anti-cheat")
public class AntiCheatProperties {

    private static final int DEFAULT_RECENT_EVENTS_LIMIT = 20;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_FUTURE_SKEW_MINUTES = 5;
    private static final int DEFAULT_REPEAT_WINDOW_MINUTES = 3;
    private static final int DEFAULT_REPEAT_PENALTY_SCORE = 2;
    private static final int DEFAULT_LEVEL_STEP_PERCENT = 25;
    private static final int DEFAULT_MEDIUM_THRESHOLD = 20;
    private static final int DEFAULT_HIGH_THRESHOLD = 50;
    private static final int DEFAULT_CRITICAL_THRESHOLD = 80;
    private static final Map<String, Integer> DEFAULT_EVENT_BASE_SCORES = Map.of(
            "SWITCH_SCREEN", 5,
            "WINDOW_BLUR", 3,
            "COPY_ATTEMPT", 8,
            "PASTE_ATTEMPT", 8,
            "DEVTOOLS_OPEN", 10,
            "NETWORK_DISCONNECT", 6,
            "MULTI_DEVICE_LOGIN", 15,
            "IDLE_TIMEOUT", 4,
            "OTHER", 2
    );

    private Integer recentEventsLimit = DEFAULT_RECENT_EVENTS_LIMIT;
    private Integer pageDefaultSize = DEFAULT_PAGE_SIZE;
    private Integer pageMaxSize = DEFAULT_MAX_PAGE_SIZE;
    private Integer maxFutureSkewMinutes = DEFAULT_MAX_FUTURE_SKEW_MINUTES;
    private Integer repeatWindowMinutes = DEFAULT_REPEAT_WINDOW_MINUTES;
    private Integer repeatPenaltyScore = DEFAULT_REPEAT_PENALTY_SCORE;
    private Integer levelStepPercent = DEFAULT_LEVEL_STEP_PERCENT;
    private Integer mediumThreshold = DEFAULT_MEDIUM_THRESHOLD;
    private Integer highThreshold = DEFAULT_HIGH_THRESHOLD;
    private Integer criticalThreshold = DEFAULT_CRITICAL_THRESHOLD;
    private Map<String, Integer> eventBaseScores = new HashMap<>(DEFAULT_EVENT_BASE_SCORES);

    public int getRecentEventsLimit() {
        return normalizePositive(recentEventsLimit, DEFAULT_RECENT_EVENTS_LIMIT);
    }

    public void setRecentEventsLimit(Integer recentEventsLimit) {
        this.recentEventsLimit = recentEventsLimit;
    }

    public int getPageDefaultSize() {
        return normalizePositive(pageDefaultSize, DEFAULT_PAGE_SIZE);
    }

    public void setPageDefaultSize(Integer pageDefaultSize) {
        this.pageDefaultSize = pageDefaultSize;
    }

    public int getPageMaxSize() {
        return Math.max(getPageDefaultSize(), normalizePositive(pageMaxSize, DEFAULT_MAX_PAGE_SIZE));
    }

    public void setPageMaxSize(Integer pageMaxSize) {
        this.pageMaxSize = pageMaxSize;
    }

    public int getMaxFutureSkewMinutes() {
        return normalizePositive(maxFutureSkewMinutes, DEFAULT_MAX_FUTURE_SKEW_MINUTES);
    }

    public void setMaxFutureSkewMinutes(Integer maxFutureSkewMinutes) {
        this.maxFutureSkewMinutes = maxFutureSkewMinutes;
    }

    public int getRepeatWindowMinutes() {
        return normalizePositive(repeatWindowMinutes, DEFAULT_REPEAT_WINDOW_MINUTES);
    }

    public void setRepeatWindowMinutes(Integer repeatWindowMinutes) {
        this.repeatWindowMinutes = repeatWindowMinutes;
    }

    public int getRepeatPenaltyScore() {
        return normalizePositive(repeatPenaltyScore, DEFAULT_REPEAT_PENALTY_SCORE);
    }

    public void setRepeatPenaltyScore(Integer repeatPenaltyScore) {
        this.repeatPenaltyScore = repeatPenaltyScore;
    }

    public int getLevelStepPercent() {
        return normalizePositive(levelStepPercent, DEFAULT_LEVEL_STEP_PERCENT);
    }

    public void setLevelStepPercent(Integer levelStepPercent) {
        this.levelStepPercent = levelStepPercent;
    }

    public int getMediumThreshold() {
        return normalizePositive(mediumThreshold, DEFAULT_MEDIUM_THRESHOLD);
    }

    public void setMediumThreshold(Integer mediumThreshold) {
        this.mediumThreshold = mediumThreshold;
    }

    public int getHighThreshold() {
        return normalizePositive(highThreshold, DEFAULT_HIGH_THRESHOLD);
    }

    public void setHighThreshold(Integer highThreshold) {
        this.highThreshold = highThreshold;
    }

    public int getCriticalThreshold() {
        return normalizePositive(criticalThreshold, DEFAULT_CRITICAL_THRESHOLD);
    }

    public void setCriticalThreshold(Integer criticalThreshold) {
        this.criticalThreshold = criticalThreshold;
    }

    public Map<String, Integer> getEventBaseScores() {
        return eventBaseScores;
    }

    public void setEventBaseScores(Map<String, Integer> eventBaseScores) {
        this.eventBaseScores = eventBaseScores == null || eventBaseScores.isEmpty()
                ? new HashMap<>(DEFAULT_EVENT_BASE_SCORES)
                : eventBaseScores;
    }

    public int resolveBaseScore(String eventType) {
        String normalizedEventType = normalizeEventType(eventType);
        if (eventBaseScores != null) {
            Integer configured = eventBaseScores.get(normalizedEventType);
            if (configured != null && configured > 0) {
                return configured;
            }
            Integer fallback = eventBaseScores.get("OTHER");
            if (fallback != null && fallback > 0) {
                return fallback;
            }
        }
        return DEFAULT_EVENT_BASE_SCORES.get("OTHER");
    }

    public String normalizeEventType(String eventType) {
        if (eventType == null) {
            return "OTHER";
        }
        String normalized = eventType.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return normalized.isEmpty() ? "OTHER" : normalized;
    }

    private int normalizePositive(Integer value, int defaultValue) {
        if (value == null || value < 1) {
            return defaultValue;
        }
        return value;
    }
}
