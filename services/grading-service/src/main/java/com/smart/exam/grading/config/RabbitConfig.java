package com.smart.exam.grading.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXAM_EXCHANGE = "exam.exchange";
    public static final String EXAM_SUBMITTED_QUEUE = "exam.submitted.q";
    public static final String SCORE_PUBLISHED_QUEUE = "score.published.q";
    public static final String EXAM_SUBMITTED_ROUTING_KEY = "exam.submitted";
    public static final String SCORE_PUBLISHED_ROUTING_KEY = "score.published";

    @Bean
    public TopicExchange examExchange() {
        return new TopicExchange(EXAM_EXCHANGE, true, false);
    }

    @Bean
    public Queue examSubmittedQueue() {
        return new Queue(EXAM_SUBMITTED_QUEUE, true);
    }

    @Bean
    public Queue scorePublishedQueue() {
        return new Queue(SCORE_PUBLISHED_QUEUE, true);
    }

    @Bean
    public Binding examSubmittedBinding(Queue examSubmittedQueue, TopicExchange examExchange) {
        return BindingBuilder.bind(examSubmittedQueue).to(examExchange).with(EXAM_SUBMITTED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
