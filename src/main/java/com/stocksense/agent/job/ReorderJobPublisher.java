package com.stocksense.agent.job;

import com.stocksense.config.RabbitConfig;
import com.stocksense.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Publishes reorder-scan jobs onto the broker so they run off the request thread. */
@Component
@RequiredArgsConstructor
public class ReorderJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public String enqueue(Long branchId, Long userId, int horizonDays) {

        var jobId = UUID.randomUUID().toString();
        var job = new ReorderJob(jobId
                , TenantContext.get()
                , branchId
                , userId
                , horizonDays);

        rabbitTemplate.convertAndSend(RabbitConfig.REORDER_QUEUE, job);

        return jobId;
    }
}
