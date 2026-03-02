package com.smart.exam.analysis.mq;

import com.smart.exam.analysis.config.RabbitConfig;
import com.smart.exam.analysis.service.ReportDomainService;
import com.smart.exam.common.core.event.ScorePublishedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ScorePublishedListener {

    private final ReportDomainService reportDomainService;

    public ScorePublishedListener(ReportDomainService reportDomainService) {
        this.reportDomainService = reportDomainService;
    }

    @RabbitListener(queues = RabbitConfig.SCORE_PUBLISHED_QUEUE)
    public void onMessage(ScorePublishedEvent event) {
        reportDomainService.onScorePublished(event);
    }
}

