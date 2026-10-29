package cn.bcd.app.bp.backend.base.support_spring_cache;

import cn.bcd.app.bp.backend.base.support_spring_cache.anno.MultiCacheable;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiCacheTest {
    @Test
    void concurrentAnnotationMissesOnlyExecuteTheMethodOnce() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Service service = context.getBean(Service.class);
            CacheManager manager = context.getBean(CacheManager.class);
            Cache redis = manager.getCache(CacheConst.REDIS_CACHE);
            clearInvocations(redis);
            try (var executor = Executors.newFixedThreadPool(2)) {
                try {
                    Future<String> first = executor.submit(() -> service.concurrent(1));
                    assertTrue(service.awaitLoading());
                    Future<String> second = executor.submit(() -> service.concurrent(1));
                    verify(redis, timeout(5000).times(2)).get(any(), any(Callable.class));
                    service.releaseLoading();
                    assertEquals("loaded", first.get(5, TimeUnit.SECONDS));
                    assertEquals("loaded", second.get(5, TimeUnit.SECONDS));
                    assertEquals(1, service.calls());
                    Object key = new MySimpleKeyGenerator().generate(new Service(), Service.class.getMethod("concurrent", int.class), 1);
                    assertEquals("loaded", manager.getCache(CacheConst.LOCAL_CACHE).get(key).get());
                    assertEquals("loaded", redis.get(key).get());
                } finally {
                    service.releaseLoading();
                }
            }
        }
    }

    @Test
    void annotationRepopulatesLocalCacheWithoutLoadingOrRefreshingRedis() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Service service = context.getBean(Service.class);
            CacheManager manager = context.getBean(CacheManager.class);
            Cache local = manager.getCache(CacheConst.LOCAL_CACHE);
            Cache redis = manager.getCache(CacheConst.REDIS_CACHE);
            Object key = new MySimpleKeyGenerator().generate(new Service(), Service.class.getMethod("get", int.class), 1);
            assertEquals("loaded", service.get(1));
            local.clear();
            clearInvocations(redis);
            assertEquals("loaded", service.get(1));
            assertEquals("loaded", local.get(key).get());
            assertEquals(1, service.calls());
            verify(redis, never()).put(any(), any());
            clearInvocations(redis);
            assertEquals("loaded", service.get(1));
            verifyNoInteractions(redis);
        }
    }

    @Test
    void cachedNullIsRepopulatedAndDoesNotCallLoader() {
        Cache local = new CacheConfig().localCache();
        Cache redis = new ConcurrentMapCache(CacheConst.REDIS_CACHE);
        MultiCache cache = new MultiCache(local, redis);
        redis.put("key", null);
        assertNotNull(cache.get("key"));
        assertNull(cache.get("key").get());
        assertNotNull(local.get("key"));
        assertNull(cache.get("key", () -> { fail("cached null must be a hit"); return "loaded"; }));
    }

    @Test
    void loadingReadsRedisBeforeCallingLoaderAndPopulatesBothCachesOnMiss() {
        Cache local = new CacheConfig().localCache();
        Cache redis = new ConcurrentMapCache(CacheConst.REDIS_CACHE);
        MultiCache cache = new MultiCache(local, redis);
        redis.put("existing", "remote");
        assertEquals("remote", cache.get("existing", () -> { fail("Redis hit"); return "loaded"; }));
        assertEquals("remote", local.get("existing").get());
        AtomicInteger calls = new AtomicInteger();
        assertEquals("loaded", cache.get("missing", () -> { calls.incrementAndGet(); return "loaded"; }));
        assertEquals("loaded", redis.get("missing").get());
        assertEquals("loaded", local.get("missing").get());
        assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> cache.get("existing", Integer.class));
    }

    @Test
    void writesAndInvalidationAffectBothCaches() {
        Cache local = new CacheConfig().localCache();
        Cache redis = new ConcurrentMapCache(CacheConst.REDIS_CACHE);
        MultiCache cache = new MultiCache(local, redis);
        cache.put("key", "one");
        assertEquals("one", local.get("key").get());
        assertEquals("one", redis.get("key").get());
        assertEquals("one", cache.putIfAbsent("key", "two").get());
        assertEquals("one", local.get("key").get());
        assertTrue(cache.evictIfPresent("key"));
        assertNull(local.get("key"));
        assertNull(redis.get("key"));
        assertNull(cache.putIfAbsent("key", "three"));
        cache.evict("key");
        assertNull(cache.get("key"));
        cache.put("key", "four");
        cache.clear();
        assertNull(local.get("key"));
        assertNull(redis.get("key"));
        cache.put("key", "five");
        assertTrue(cache.invalidate());
        assertNull(local.get("key"));
        assertNull(redis.get("key"));
    }

    @Test
    void loaderFailureIsNotCachedAndKeepsItsOriginalCause() {
        Cache local = new CacheConfig().localCache();
        MultiCache cache = new MultiCache(local, new ConcurrentMapCache(CacheConst.REDIS_CACHE));
        IllegalArgumentException failure = new IllegalArgumentException("failure");
        Cache.ValueRetrievalException thrown = assertThrows(Cache.ValueRetrievalException.class,
                () -> cache.get("key", () -> { throw failure; }));
        assertSame(failure, thrown.getCause());
        assertNull(local.get("key"));
        assertEquals("recovered", cache.get("key", () -> "recovered"));
    }

    @Configuration
    @Import(CacheConfig.class)
    static class Config {
        @Bean CacheManager cacheManager(CacheConfig config) {
            Cache local = config.localCache();
            Cache redis = spy(new ConcurrentMapCache(CacheConst.REDIS_CACHE));
            Cache multi = new RedisCacheConfig().multiCache(local, redis);
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(local, redis, multi));
            return manager;
        }
        @Bean Service service() { return new Service(); }
    }

    public static class Service {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch loading = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        @MultiCacheable public String get(int id) { calls.incrementAndGet(); return "loaded"; }
        @MultiCacheable public String concurrent(int id) throws InterruptedException {
            calls.incrementAndGet();
            loading.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("loader timed out");
            }
            return "loaded";
        }
        public boolean awaitLoading() throws InterruptedException { return loading.await(5, TimeUnit.SECONDS); }
        public void releaseLoading() { release.countDown(); }
        public int calls() { return calls.get(); }
    }
}
