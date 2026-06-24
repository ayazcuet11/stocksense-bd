package com.stocksense.security;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        Long id = CURRENT.get();
        if (id == null) throw new IllegalStateException("No tenant in context");
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
