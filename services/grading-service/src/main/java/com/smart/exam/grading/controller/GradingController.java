package com.smart.exam.grading.controller;

import com.smart.exam.common.core.model.ApiResponse;
import com.smart.exam.grading.dto.ManualScoreRequest;
import com.smart.exam.grading.model.GradingTask;
import com.smart.exam.grading.service.GradingDomainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/grading")
public class GradingController {

    private final GradingDomainService gradingDomainService;

    public GradingController(GradingDomainService gradingDomainService) {
        this.gradingDomainService = gradingDomainService;
    }

    @GetMapping("/tasks")
    public ApiResponse<Collection<GradingTask>> listTasks(@RequestParam(required = false) String status) {
        return ApiResponse.ok(gradingDomainService.listTasks(status));
    }

    @PostMapping("/tasks/{taskId}/manual-score")
    public ApiResponse<GradingTask> manualScore(
            @PathVariable String taskId,
            @Valid @RequestBody ManualScoreRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "20001") String graderId) {
        return ApiResponse.ok(gradingDomainService.manualScore(taskId, request, graderId));
    }
}

