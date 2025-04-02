package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.server.business.process.backend.sys.bean.UserBean;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class CacheService {
    @Autowired
    UserService userService;

    @Autowired
    RoleService roleService;

    @Autowired
    PermissionService permissionService;

    final Duration expire = Duration.ofSeconds(3);

    private volatile LoadingCache<String, UserBean> username_user;
    private volatile LoadingCache<String, List<String>> username_roleList;
    private volatile LoadingCache<String, List<String>> username_permissionList;

    public UserBean getUser(String username) {
        if (username_user == null) {
            synchronized (this) {
                if (username_user == null) {
                    username_user = Caffeine.newBuilder().expireAfterWrite(expire).build(k -> userService.getUser(k));
                }
            }
        }
        return username_user.get(username);
    }

    public List<String> getRoleList(String username, String loginType) {
        if (username_roleList == null) {
            synchronized (this) {
                if (username_roleList == null) {
                    username_roleList = Caffeine.newBuilder().expireAfterWrite(expire).build(k -> roleService.findRolesByUsername(k).stream().map(e->e.code).toList());
                }
            }
        }
        return username_roleList.get(username);
    }

    public List<String> getPermissionList(String username, String loginType) {
        if (username_permissionList == null) {
            synchronized (this) {
                if (username_permissionList == null) {
                    username_permissionList = Caffeine.newBuilder().expireAfterWrite(expire).build(k -> permissionService.findPermissionsByUsername(k).stream().map(e->e.resource).toList());
                }
            }
        }
        return username_permissionList.get(username);
    }
}
