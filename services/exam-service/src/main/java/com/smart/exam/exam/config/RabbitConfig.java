package com.smart.exam.exam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXAM_EXCHANGE = "exam.exchange";
    public static final String EXAM_SUBMITTED_ROUTING_KEY = "exam.submitted";

    @Bean
    public TopicExchange examExchange() {
        return new TopicExchange(EXAM_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
