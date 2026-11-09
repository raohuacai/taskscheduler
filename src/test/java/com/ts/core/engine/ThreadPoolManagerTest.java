package com.ts.core.engine;

import com.ts.config.ThreadPoolConfig;
import com.ts.service.ParamsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ThreadPoolManager}: executor creation applies the configured
 * pool settings; graceful shutdown works.
 */
@ExtendWith(MockitoExtension.class)
class ThreadPoolManagerTest {

    @Mock
    private ParamsService paramsService;

    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() {
        manager = new ThreadPoolManager(paramsService);
    }

    @Test
    void createExecutorService_appliesConfiguration() {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setCorePoolSize(2);
        config.setMaxPoolSize(5);
        config.setKeepAliveSeconds(30);
        config.setQueueCapacity(100);
        config.setRejectionPolicy("CallerRunsPolicy");
        when(paramsService.getThreadPoolConfig()).thenReturn(config);

        ThreadPoolExecutor exec = manager.createExecutorService();
        try {
            assertEquals(2, exec.getCorePoolSize());
            assertEquals(5, exec.getMaximumPoolSize());
            assertEquals(30, exec.getKeepAliveTime(TimeUnit.SECONDS));
            assertTrue(exec.allowsCoreThreadTimeOut());
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void createExecutorService_defaultsWhenNoConfig() {
        // config null -> ThreadPoolManager falls back to a default config
        when(paramsService.getThreadPoolConfig()).thenReturn(new ThreadPoolConfig());

        ThreadPoolExecutor exec = manager.createExecutorService();
        try {
            assertNotNull(exec);
            assertTrue(exec.getCorePoolSize() > 0);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void shutdownExecutorService_completesGracefully() {
        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        exec.execute(() -> { /* trivial task */ });

        assertTrue(manager.shutdownExecutorService(exec, 5, TimeUnit.SECONDS));
    }

    @Test
    void shutdownExecutorService_alreadyShutdown_returnsTrue() {
        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        exec.shutdown();

        assertTrue(manager.shutdownExecutorService(exec, 5, TimeUnit.SECONDS));
    }
}
