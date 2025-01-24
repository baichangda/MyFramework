package cn.bcd.businessProcess.backend.base.support_spring_cache.anno;

import cn.bcd.businessProcess.backend.base.support_spring_cache.CacheConst;
import org.springframework.cache.annotation.CachePut;

import java.lang.annotation.*;

@CachePut(cacheNames = {CacheConst.LOCAL_CACHE,CacheConst.REDIS_CACHE},keyGenerator = CacheConst.KEY_GENERATOR)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiCachePut {
    
}
