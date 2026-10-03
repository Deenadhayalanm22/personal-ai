package com.apps.deen_sa.cache;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the production aspect alongside Spring's real transaction interceptor. */
class PortalCacheTransactionTest {
    @Test void commitInvalidatesAfterDatabaseCommitAndRollbackDoesNot() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var cache = context.getBean(PortalReadCache.class);
            var writer = context.getBean(Writer.class);
            var manager = context.getBean(TrackingTransactionManager.class);
            doAnswer(call -> { assertTrue(manager.committed); return null; }).when(cache).invalidate();
            writer.write(); verify(cache).invalidate();
            clearInvocations(cache); manager.committed = false;
            assertThrows(IllegalArgumentException.class, writer::fail);
            assertFalse(manager.committed); verifyNoInteractions(cache);
        }
    }

    @Test void standaloneCrudRepositorySaveIsAdvised() {
        PortalReadCache cache = mock(PortalReadCache.class);
        @SuppressWarnings("unchecked") CrudRepository<Object, Long> repository = mock(CrudRepository.class);
        var factory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(repository);
        factory.addAspect(new PortalCacheInvalidationAspect(cache));
        CrudRepository<Object, Long> proxy = factory.getProxy();
        proxy.save("row"); verify(cache).invalidate();
        clearInvocations(cache); proxy.findById(1L); verifyNoInteractions(cache);
    }

    @Configuration
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    static class Config {
        @Bean PortalReadCache cache() { return mock(PortalReadCache.class); }
        @Bean PortalCacheInvalidationAspect invalidation(PortalReadCache cache) { return new PortalCacheInvalidationAspect(cache); }
        @Bean TrackingTransactionManager transactionManager() { return new TrackingTransactionManager(); }
        @Bean Writer writer(PortalReadCache cache) { return new Writer(cache); }
    }

    public static class Writer {
        private final PortalReadCache cache;
        Writer(PortalReadCache cache) { this.cache = cache; }
        @Transactional public void write() { verifyNoInteractions(cache); }
        @Transactional public void fail() { throw new IllegalArgumentException(); }
    }

    static class TrackingTransactionManager extends AbstractPlatformTransactionManager {
        boolean committed;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
