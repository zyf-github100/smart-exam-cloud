package com.smart.exam.exam.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExamStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamStatusScheduler.class);

    private final ExamDomainService examDomainService;
    private final long autoForceSubmitBatchSize;

    public ExamStatusScheduler(
            ExamDomainService examDomainService,
            @Value("${smart-exam.exam.auto-force-submit.batch-size:200}") long autoForceSubmitBatchSize) {
        this.examDomainService = examDomainService;
        this.autoForceSubmitBatchSize = autoForceSubmitBatchSize;
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

    @Scheduled(
            initialDelayString = "${smart-exam.exam.auto-force-submit.initial-delay-ms:15000}",
            fixedDelayString = "${smart-exam.exam.auto-force-submit.interval-ms:15000}"
    )
    public void autoForceSubmitExpiredSessions() {
        List<Long> sessionIds = examDomainService.listExpiredInProgressSessionIds(autoForceSubmitBatchSize);
        if (sessionIds.isEmpty()) {
            return;
        }

        int submitted = 0;
        for (Long sessionId : sessionIds) {
            try {
                if (examDomainService.forceSubmitExpiredSession(sessionId)) {
                    submitted++;
                }
            } catch (Exception ex) {
                log.warn("Failed to auto force submit session, sessionId={}", sessionId, ex);
            }
        }
        if (submitted > 0) {
            log.info("Auto force submitted {} expired sessions", submitted);
        }
    }
}
