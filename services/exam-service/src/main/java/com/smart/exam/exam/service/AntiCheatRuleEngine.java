package com.smart.exam.exam.service;

import com.smart.exam.exam.config.AntiCheatProperties;
import com.smart.exam.exam.entity.SessionRiskSummaryEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class AntiCheatRuleEngine {

    private final AntiCheatProperties antiCheatProperties;

    public AntiCheatRuleEngine(AntiCheatProperties antiCheatProperties) {
        this.antiCheatProperties = antiCheatProperties;
    }

    public int calculateScore(String eventType,
                              Integer antiCheatLevel,
                              SessionRiskSummaryEntity currentSummary,
                              LocalDateTime eventTime) {
        String normalizedEventType = normalizeEventType(eventType);
        int baseScore = antiCheatProperties.resolveBaseScore(normalizedEventType);
        int level = antiCheatLevel == null ? 0 : Math.max(0, Math.min(antiCheatLevel, 5));
        double multiplier = 1.0 + Math.max(0, level - 1) * (antiCheatProperties.getLevelStepPercent() / 100.0);
        int weightedScore = Math.max(1, (int) Math.round(baseScore * multiplier));

        int burstPenalty = 0;
        if (currentSummary != null
                && normalizedEventType.equals(normalizeEventType(currentSummary.getLastEventType()))
                && currentSummary.getLastEventTime() != null
                && eventTime != null
                && !eventTime.isBefore(currentSummary.getLastEventTime())) {
            long minutes = Duration.between(currentSummary.getLastEventTime(), eventTime).toMinutes();
            if (minutes <= antiCheatProperties.getRepeatWindowMinutes()) {
                burstPenalty = antiCheatProperties.getRepeatPenaltyScore();
            }
        }

        return weightedScore + burstPenalty;
    }

    public String normalizeEventType(String rawEventType) {
        return antiCheatProperties.normalizeEventType(rawEventType);
    }

    public String resolveRiskLevel(int riskScore) {
        int criticalThreshold = Math.max(3, antiCheatProperties.getCriticalThreshold());
        int highThreshold = Math.max(2, Math.min(criticalThreshold - 1, antiCheatProperties.getHighThreshold()));
        int mediumThreshold = Math.max(1, Math.min(highThreshold - 1, antiCheatProperties.getMediumThreshold()));

        if (riskScore >= criticalThreshold) {
            return "CRITICAL";
        }
        if (riskScore >= highThreshold) {
            return "HIGH";
        }
        if (riskScore >= mediumThreshold) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
