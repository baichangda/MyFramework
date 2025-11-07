package cn.bcd.app.businessProcess.gateway;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.cloud.common.fegin.user.AuthUser;
import cn.bcd.lib.spring.cloud.common.fegin.user.UserClient;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class CacheService {

    static Logger logger = LoggerFactory.getLogger(CacheService.class);

    @Autowired
    UserClient userClient;

    final Duration expire = Duration.ofSeconds(3);

    private volatile LoadingCache<String, AuthUser> username_user;
    private volatile LoadingCache<String, List<String>> username_roleList;
    private volatile LoadingCache<String, List<String>> username_permissionList;

    public AuthUser getUser(String username) {
        if (username_user == null) {
            synchronized (this) {
                if (username_user == null) {
                    username_user = Caffeine.newBuilder().expireAfterWrite(expire).build(k -> {
                        Result<AuthUser> result = CompletableFuture.supplyAsync(() -> userClient.getAuthUser(k)).join();
                        if (result.code == 0) {
                            return result.data;
                        } else {
                            logger.warn("getUser error:\n{}", JsonUtil.toJson(result));
                            return null;
                        }
                    });
                }
            }
        }
        return username_user.get(username);
    }

    public List<String> getRoleList(String username, String loginType) {
        if (username_roleList == null) {
            synchronized (this) {
                if (username_roleList == null) {
                    username_roleList = Caffeine.newBuilder().expireAfterWrite(expire).build(k -> {
                        int i = k.indexOf(",");
                        String s1 = k.substring(0, i);
                        String s2 = k.substring(i + 1);
                        Result<List<String>> result = CompletableFuture.supplyAsync(() -> userClient.getUserRoles(s1, s2)).join();
                        if (result.code == 0) {
                            return result.data;
                        } else {
                            logger.warn("getRoleList error:\n{}", JsonUtil.toJson(result));
                            return Collections.emptyList();
                        }
                    });
                }
            }
        }
        return username_roleList.get(username + "," + loginType);
    }

    public List<String> getPermissionList(String username, String loginType) {
        if (username_permissionList == null) {
            synchronized (this) {
                if (username_permissionList == null) {
                    username_permissionList = Caffeine.newBuilder().expireAfterWrite(expire).build(k -> {
                        int i = k.indexOf(",");
                        String s1 = k.substring(0, i);
                        String s2 = k.substring(i + 1);
                        Result<List<String>> result = CompletableFuture.supplyAsync(() -> userClient.getUserPermissions(s1, s2)).join();
                        if (result.code == 0) {
                            return result.data;
                        } else {
                            logger.warn("getPermissionList error:\n{}", JsonUtil.toJson(result));
                            return Collections.emptyList();
                        }
                    });
                }
            }
        }
        return username_permissionList.get(username + "," + loginType);
    }
}
