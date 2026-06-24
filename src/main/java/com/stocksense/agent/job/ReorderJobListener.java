package com.stocksense.agent.job;

import com.stocksense.agent.SmartReorderAgent;
import com.stocksense.config.RabbitConfig;
import com.stocksense.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes reorder-scan jobs and runs the {@link SmartReorderAgent} off the request thread. Sets the
 * tenant context from the job payload (the ThreadLocal does not cross threads) and always clears it.
 */
@Component
public class ReorderJobListener {

    private static final Logger log = LoggerFactory.getLogger(ReorderJobListener.class);

    private final SmartReorderAgent agent;

    public ReorderJobListener(SmartReorderAgent agent) {
        this.agent = agent;
    }

    @RabbitListener(queues = RabbitConfig.REORDER_QUEUE)
    public void handle(ReorderJob job) {

        log.info("Reorder job {} received: tenant={}, branch={}"
                , job.jobId()
                , job.tenantId()
                , job.branchId());

        TenantContext.set(job.tenantId());

        try {
            agent.scan(job.branchId(), job.horizonDays());

        } catch (Exception ex) {
            log.error("Reorder job {} failed: {}"
                    , job.jobId()
                    , ex.getMessage()
                    , ex);

        } finally {
            TenantContext.clear();
        }
    }
}
