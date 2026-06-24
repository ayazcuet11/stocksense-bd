package com.stocksense.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Async agent jobs (Phase 4). Reorder scans are published to a durable queue and consumed off the
 * request thread by {@code ReorderJobListener}. Uses the Jackson 2 converter to match the rest of
 * the codebase (Spring Boot 4 ships both Jackson 2 and 3 variants).
 */
@Configuration
public class RabbitConfig {

    public static final String REORDER_QUEUE = "stocksense.reorder.scan";

    @Bean
    public Queue reorderQueue() {
        return new Queue(REORDER_QUEUE, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter(new ObjectMapper());
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {

        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);

        return template;
    }
}
