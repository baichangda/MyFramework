package cn.bcd.app.businessProcess.gateway;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.spring.cloud.common.fegin.user.AuthUser;
import cn.bcd.lib.spring.cloud.common.fegin.user.UserClient;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

@Component
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final long MAXIMUM_CACHE_SIZE = 10_000;

    private final UserClient userClient;
    private final LoadingCache<String, AuthUser> users;
    private final LoadingCache<AuthCacheKey, List<String>> roles;
    private final LoadingCache<AuthCacheKey, List<String>> permissions;

    public CacheService(UserClient userClient) {
        this.userClient = userClient;
        users = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .build(this::loadUser);
        roles = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .build(this::loadRoles);
        permissions = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .build(this::loadPermissions);
    }

    public AuthUser getUser(String username) {
        return users.get(username);
    }

    public List<String> getRoleList(String username, String loginType) {
        return roles.get(new AuthCacheKey(username, loginType));
    }

    public List<String> getPermissionList(String username, String loginType) {
        return permissions.get(new AuthCacheKey(username, loginType));
    }

    private AuthUser loadUser(String username) {
        try {
            Result<AuthUser> result = userClient.getAuthUser(username);
            return result.code == 0 ? result.data : null;
        } catch (Exception ex) {
            logger.error("getUser error key[{}]", username, ex);
            return null;
        }
    }

    private List<String> loadRoles(AuthCacheKey key) {
        return loadAuthorities(key, userClient::getUserRoles, "getRoleList");
    }

    private List<String> loadPermissions(AuthCacheKey key) {
        return loadAuthorities(key, userClient::getUserPermissions, "getPermissionList");
    }

    private List<String> loadAuthorities(
            AuthCacheKey key,
            BiFunction<String, String, Result<List<String>>> loader,
            String operation
    ) {
        Result<List<String>> result = loader.apply(key.username(), key.loginType());
        if (result.code == 0) {
            return result.data;
        }
        logger.warn("{} error:\n{}", operation, JsonUtil.toJson(result));
        return Collections.emptyList();
    }

    private record AuthCacheKey(String username, String loginType) {
    }
}
