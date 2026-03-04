package com.smart.exam.exam.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExamStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamStatusScheduler.class);

    private final ExamDomainService examDomainService;

    public ExamStatusScheduler(ExamDomainService examDomainService) {
        this.examDomainService = examDomainService;
    }

    @Scheduled(
            initialDelayString = "${smart-exam.exam.status-sync-initial-delay-ms:10000}",
            fixedDelayString = "${smart-exam.exam.status-sync-interval-ms:30000}"
    )
    public void syncExamStatus() {
        int updated = examDomainService.syncExamStatuses();
        if (updated > 0) {
            log.info("Exam status auto-transition updated {} records", updated);
        }
    }
}

