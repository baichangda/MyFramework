package cn.bcd.business.process.gateway;

import cn.bcd.lib.microservice.common.fegin.user.UserClient;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class CacheService {
    @Autowired
    UserClient userClient;

    final Duration expire = Duration.ofSeconds(3);

    private volatile LoadingCache<String, List<String>> username_roleList;
    private volatile LoadingCache<String, List<String>> username_permissionList;


    public List<String> getRoleList(String username, String loginType) {
        if (username_roleList == null) {
            synchronized (this) {
                if (username_roleList == null) {
                    username_roleList = Caffeine.newBuilder().expireAfterWrite(expire).build(k -> {
                        int i = k.indexOf(",");
                        String s1 = k.substring(0, i);
                        String s2 = k.substring(i + 1);
                        return userClient.getUserRoles(s1, s2);
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
                        return userClient.getUserPermissions(s1, s2);
                    });
                }
            }
        }
        return username_permissionList.get(username + "," + loginType);
    }
}
