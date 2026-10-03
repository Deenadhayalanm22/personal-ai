package com.apps.deen_sa.cache;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalCacheInvalidationAspectTest {
    final PortalReadCache cache = mock(PortalReadCache.class);
    final PortalCacheInvalidationAspect aspect = new PortalCacheInvalidationAspect(cache);

    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void invalidatesOnlyAfterCommitAndDeduplicatesNestedWriters() {
        TransactionSynchronizationManager.initSynchronization();
        aspect.afterCommit(); aspect.afterCommit();
        verifyNoInteractions(cache);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cache).invalidate();
    }

    @Test void rolledBackTransactionDoesNotInvalidate() {
        TransactionSynchronizationManager.initSynchronization();
        aspect.afterCommit();
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(cache);
    }

    @Test void completedStandaloneWritesInvalidate() { aspect.afterCommit(); verify(cache).invalidate(); }

    @Test void actualSpringProxyAdvisesWritesButNotReadOnlyTransactions() {
        var factory = new AspectJProxyFactory(new Writer()); factory.addAspect(aspect);
        Writer proxy = factory.getProxy();
        proxy.read(); verifyNoInteractions(cache);
        proxy.write(); verify(cache).invalidate();
        assertThrows(IllegalArgumentException.class, proxy::fail);
        verifyNoMoreInteractions(cache);
    }

    @Test void failedRepositoryWriteDoesNotInvalidate() throws Throwable {
        var call = mock(ProceedingJoinPoint.class);
        when(call.proceed()).thenThrow(new IllegalArgumentException());
        assertThrows(IllegalArgumentException.class, () -> aspect.repositoryWrite(call));
        verifyNoInteractions(cache);
    }

    public static class Writer {
        @Transactional(readOnly = true) public void read() { }
        @Transactional public void write() { }
        @Transactional public void fail() { throw new IllegalArgumentException(); }
    }
}
