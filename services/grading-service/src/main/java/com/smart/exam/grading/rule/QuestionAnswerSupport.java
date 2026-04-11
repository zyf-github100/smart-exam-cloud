package com.smart.exam.grading.rule;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class QuestionAnswerSupport {

    private static final String TOKEN_SPLIT_REGEX = "[,\\uFF0C锛孿\\s]+";

    private QuestionAnswerSupport() {
    }

    public static String normalizeToken(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
    }

    public static String extractFirstScalar(JsonNode answerNode) {
        if (answerNode == null || answerNode.isNull()) {
            return null;
        }
        if (answerNode.isArray()) {
            if (answerNode.isEmpty()) {
                return null;
            }
            return answerNode.get(0).asText();
        }
        return answerNode.asText();
    }

    public static Set<String> splitTokens(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Set.of();
        }
        String[] parts = rawValue.split(TOKEN_SPLIT_REGEX);
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            String token = normalizeToken(part);
            if (StringUtils.hasText(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static List<String> splitTokensOrdered(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }
        String[] parts = rawValue.split(TOKEN_SPLIT_REGEX);
        LinkedHashMap<String, String> dedup = new LinkedHashMap<>();
        for (String part : parts) {
            String token = normalizeToken(part);
            if (StringUtils.hasText(token) && !dedup.containsKey(token)) {
                dedup.put(token, token);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    public static Set<String> readMultiAnswerTokens(JsonNode answerNode) {
        if (answerNode == null || answerNode.isNull()) {
            return Set.of();
        }
        if (answerNode.isArray()) {
            Set<String> tokens = new HashSet<>();
            for (JsonNode node : answerNode) {
                String token = normalizeToken(node == null ? null : node.asText());
                if (StringUtils.hasText(token)) {
                    tokens.add(token);
                }
            }
            return tokens;
        }
        return splitTokens(extractFirstScalar(answerNode));
    }

    public static Boolean parseBooleanValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "t", "yes", "y" -> true;
            case "false", "0", "f", "no", "n" -> false;
            default -> null;
        };
    }

    public static String normalizeFillAnswer(String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
    }
}
