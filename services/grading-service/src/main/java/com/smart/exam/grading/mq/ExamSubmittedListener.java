package com.smart.exam.grading.mq;

import com.smart.exam.common.core.event.ExamSubmittedEvent;
import com.smart.exam.grading.config.RabbitConfig;
import com.smart.exam.grading.service.GradingDomainService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ExamSubmittedListener {

    private final GradingDomainService gradingDomainService;

    public ExamSubmittedListener(GradingDomainService gradingDomainService) {
        this.gradingDomainService = gradingDomainService;
    }

    @RabbitListener(queues = RabbitConfig.EXAM_SUBMITTED_QUEUE)
    public void listen(ExamSubmittedEvent event) {
        gradingDomainService.onExamSubmitted(event);
    }
}

