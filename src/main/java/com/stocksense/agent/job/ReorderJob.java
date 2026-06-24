package com.stocksense.agent.job;

/**
 * Message payload for an async reorder scan. Carries the tenant explicitly because the consumer runs
 * on a listener thread where {@code TenantContext} (a ThreadLocal) is not populated.
 */
public record ReorderJob(String jobId, Long tenantId, Long branchId, Long userId, int horizonDays) {
}
