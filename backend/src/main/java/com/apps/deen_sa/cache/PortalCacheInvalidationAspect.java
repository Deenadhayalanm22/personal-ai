package com.apps.deen_sa.cache;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Conservative invalidation includes portal, WhatsApp, JDBC and scheduled writes. */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class PortalCacheInvalidationAspect {
    private final PortalReadCache cache;

    public PortalCacheInvalidationAspect(PortalReadCache cache) { this.cache = cache; }

    // These two lookups use writable transactions only to initialize missing snapshots.
    // Their actual inserts are covered by repositoryWrite; unchanged lookups must stay cacheable.
    @Around("execution(public * com.apps.deen_sa..*(..)) && @annotation(transaction) && "
            + "!execution(* com.apps.deen_sa.service.MonthlyFinancialSnapshotService.current(..)) && "
            + "!execution(* com.apps.deen_sa.service.MonthlyFinancialSnapshotService.next(..))")
    public Object transactionalWrite(ProceedingJoinPoint invocation, Transactional transaction) throws Throwable {
        Object result = invocation.proceed();
        if (!transaction.readOnly()) afterCommit();
        return result;
    }

    // Repository saves outside an application-service transaction must also invalidate. Bulk
    // @Modifying queries bypass entity callbacks, so they are covered explicitly.
    @Around("execution(* org.springframework.data.repository.CrudRepository+.save*(..)) || "
            + "execution(* org.springframework.data.repository.CrudRepository+.delete*(..)) || "
            + "execution(* org.springframework.data.jpa.repository.JpaRepository+.save*(..)) || "
            + "execution(* org.springframework.data.jpa.repository.JpaRepository+.delete*(..)) || "
            + "@annotation(org.springframework.data.jpa.repository.Modifying)")
    public Object repositoryWrite(ProceedingJoinPoint invocation) throws Throwable {
        Object result = invocation.proceed();
        afterCommit();
        return result;
    }

    void afterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.invalidate();
            return;
        }
        if (TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(Invalidation.class::isInstance)) return;
        TransactionSynchronizationManager.registerSynchronization(new Invalidation());
    }

    private final class Invalidation implements TransactionSynchronization {
        @Override public void afterCommit() { cache.invalidate(); }
    }
}
