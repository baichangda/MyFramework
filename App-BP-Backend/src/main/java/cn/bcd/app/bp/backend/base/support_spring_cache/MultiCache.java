package cn.bcd.app.bp.backend.base.support_spring_cache;

import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import java.util.List;
import java.util.concurrent.Callable;

/** Read locally first and populate the local cache on Redis hits. */
public class MultiCache extends AbstractValueAdaptingCache {
    private final Cache localCache;
    private final Cache redisCache;

    public MultiCache(Cache localCache, Cache redisCache) {
        super(true);
        this.localCache = localCache;
        this.redisCache = redisCache;
    }

    @Override
    public String getName() {
        return CacheConst.MULTI_CACHE;
    }

    @Override
    public Object getNativeCache() {
        return List.of(localCache.getNativeCache(), redisCache.getNativeCache());
    }

    @Override
    protected Object lookup(Object key) {
        ValueWrapper value = localCache.get(key);
        if (value == null) {
            value = redisCache.get(key);
            if (value != null) {
                localCache.put(key, value.get());
            }
        }
        return value == null ? null : toStoreValue(value.get());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper value = localCache.get(key);
        if (value != null) {
            return (T) value.get();
        }
        T loaded = redisCache.get(key, valueLoader);
        localCache.put(key, loaded);
        return loaded;
    }

    @Override
    public void put(Object key, Object value) {
        redisCache.put(key, value);
        localCache.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = redisCache.putIfAbsent(key, value);
        localCache.put(key, existing == null ? value : existing.get());
        return existing;
    }

    @Override
    public void evict(Object key) {
        redisCache.evict(key);
        localCache.evict(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean redisPresent = redisCache.evictIfPresent(key);
        return localCache.evictIfPresent(key) || redisPresent;
    }

    @Override
    public void clear() {
        redisCache.clear();
        localCache.clear();
    }

    @Override
    public boolean invalidate() {
        boolean redisPresent = redisCache.invalidate();
        return localCache.invalidate() || redisPresent;
    }
}
