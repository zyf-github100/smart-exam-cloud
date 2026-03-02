package com.smart.exam.analysis.service;

import com.smart.exam.common.core.event.ScorePublishedEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportDomainService {

    private static final List<String> SCORE_BUCKETS = List.of("0-59", "60-69", "70-79", "80-89", "90-100");

    private final Map<String, List<Double>> scoreStore = new ConcurrentHashMap<>();

    public void onScorePublished(ScorePublishedEvent event) {
        scoreStore.computeIfAbsent(event.getExamId(), k -> new ArrayList<>()).add(event.getTotalScore());
    }

    public Map<String, Object> scoreDistribution(String examId) {
        int[] buckets = new int[]{0, 0, 0, 0, 0};
        List<Double> scores = scoreStore.getOrDefault(examId, List.of());
        for (Double score : scores) {
            if (score < 60) {
                buckets[0]++;
            } else if (score < 70) {
                buckets[1]++;
            } else if (score < 80) {
                buckets[2]++;
            } else if (score < 90) {
                buckets[3]++;
            } else {
                buckets[4]++;
            }
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("xAxis", SCORE_BUCKETS);
        payload.put("series", List.of(buckets[0], buckets[1], buckets[2], buckets[3], buckets[4]));
        return payload;
    }

    public Map<String, Object> questionAccuracyTop(String examId, int top) {
        // 初始代码先给固定结构，后续可替换为真实聚合统计
        List<String> xAxis = new ArrayList<>();
        List<Integer> series = new ArrayList<>();
        for (int i = 1; i <= top; i++) {
            xAxis.add("Q" + i);
            series.add(Math.max(40, 95 - i * 3));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("examId", examId);
        payload.put("xAxis", xAxis);
        payload.put("series", series);
        return payload;
    }
}

