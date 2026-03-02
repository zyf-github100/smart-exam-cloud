package com.smart.exam.analysis.controller;

import com.smart.exam.analysis.service.ReportDomainService;
import com.smart.exam.common.core.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportDomainService reportDomainService;

    public ReportController(ReportDomainService reportDomainService) {
        this.reportDomainService = reportDomainService;
    }

    @GetMapping("/exams/{examId}/score-distribution")
    public ApiResponse<Map<String, Object>> scoreDistribution(@PathVariable String examId) {
        return ApiResponse.ok(reportDomainService.scoreDistribution(examId));
    }

    @GetMapping("/exams/{examId}/question-accuracy-top")
    public ApiResponse<Map<String, Object>> questionAccuracyTop(
            @PathVariable String examId,
            @RequestParam(defaultValue = "10") int top) {
        return ApiResponse.ok(reportDomainService.questionAccuracyTop(examId, top));
    }
}

