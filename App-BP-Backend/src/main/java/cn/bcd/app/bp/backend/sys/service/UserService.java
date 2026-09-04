package cn.bcd.app.bp.backend.sys.service;

import cn.bcd.app.bp.backend.sys.bean.UserBean;
import cn.bcd.app.bp.backend.sys.define.CommonConst;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class UserService extends BaseService<UserBean> implements ApplicationListener<ContextRefreshedEvent> {
    private final PermissionService permissionService;
    private final RoleService roleService;

    public UserService(PermissionService permissionService, RoleService roleService) {
        this.permissionService = permissionService;
        this.roleService = roleService;
    }

    public UserBean getUser(String username) {
        return get(StringCondition.EQUAL("username", username));
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (get(CommonConst.ADMIN_ID) != null) {
            return;
        }
        UserBean user = new UserBean();
        user.id = CommonConst.ADMIN_ID;
        user.username = CommonConst.ADMIN_USERNAME;
        user.password = encrypt(CommonConst.INITIAL_PASSWORD);
        user.status = 1;
        insert(user);
    }

    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        UserBean user = get(userId);
        if (user == null || !user.password.equals(encrypt(oldPassword))) {
            return false;
        }
        update(userId, Map.of("password", encrypt(newPassword)));
        return true;
    }

    public void resetPassword(Long userId) {
        update(userId, Map.of("password", encrypt(CommonConst.INITIAL_PASSWORD)));
    }

    public void saveUser(UserBean user) {
        if (user.id == null) {
            user.password = encrypt(CommonConst.INITIAL_PASSWORD);
            user.status = 1;
        } else {
            UserBean stored = get(user.id);
            user.password = stored.password;
        }
        save(user);
    }

    public List<String> getUserRoles(String username, String loginType) {
        return roleService.findRolesByUsername(username).stream().map(role -> role.code).toList();
    }

    public List<String> getUserPermissions(String username, String loginType) {
        return permissionService.findPermissionsByUsername(username).stream()
                .map(permission -> permission.resource)
                .toList();
    }

    private String encrypt(String password) {
        if (!CommonConst.IS_PASSWORD_ENCODED) {
            return password;
        }
        return DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
    }
}
