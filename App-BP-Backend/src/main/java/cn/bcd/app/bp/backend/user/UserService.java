package cn.bcd.app.bp.backend.user;

import cn.bcd.app.bp.backend.permission.PermissionService;
import cn.bcd.app.bp.backend.role.RoleService;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.DateCondition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
        if (get(UserConst.ADMIN_ID) != null) {
            return;
        }
        UserBean user = new UserBean();
        user.id = UserConst.ADMIN_ID;
        user.username = UserConst.ADMIN_USERNAME;
        user.password = encrypt(UserConst.INITIAL_PASSWORD);
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
        update(userId, Map.of("password", encrypt(UserConst.INITIAL_PASSWORD)));
    }

    public List<UserResponse> listUser(UserQueryRequest query) {
        return list(buildCondition(query)).stream()
                .map(this::toResponse).toList();
    }

    public Page<UserResponse> pageUser(
            UserQueryRequest query, int pageNum, int pageSize) {
        return page(buildCondition(query), PageRequest.of(pageNum - 1, pageSize)).map(this::toResponse);
    }

    protected Condition buildCondition(UserQueryRequest query) {
        return Condition.and(
                NumberCondition.EQUAL("id", query.id),
                StringCondition.ALL_LIKE("username", query.username),
                StringCondition.ALL_LIKE("email", query.email),
                StringCondition.ALL_LIKE("phone", query.phone),
                StringCondition.ALL_LIKE("realName", query.realName),
                StringCondition.ALL_LIKE("sex", query.sex),
                DateCondition.BETWEEN("birthday", query.birthdayBegin, query.birthdayEnd),
                StringCondition.ALL_LIKE("cardNumber", query.cardNumber),
                NumberCondition.EQUAL("status", query.status)
        );
    }

    public void saveUser(UserSaveRequest request) {
        if (request.id == null) {
            UserBean bean = new UserBean();
            bean.username = request.username;
            bean.email = request.email;
            bean.phone = request.phone;
            bean.realName = request.realName;
            bean.sex = request.sex;
            bean.birthday = request.birthday;
            bean.cardNumber = request.cardNumber;
            bean.status = request.status;
            // 在此补充服务端默认值和新增业务规则。
            bean.password = encrypt(UserConst.INITIAL_PASSWORD);
            bean.status = 1;
            insert(bean);
        } else {
            if (get(request.id) == null) throw new IllegalArgumentException("Record not found");
            // 在此补充操作权限和修改业务规则。
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("username", request.username);
            changes.put("email", request.email);
            changes.put("phone", request.phone);
            changes.put("realName", request.realName);
            changes.put("sex", request.sex);
            changes.put("birthday", request.birthday);
            changes.put("cardNumber", request.cardNumber);
            changes.put("status", request.status);
            update(request.id, changes);
        }
    }

    public void deleteUser(long[] ids) {
        // 在此补充操作权限和关联数据检查。
        delete(Arrays.stream(ids).distinct().toArray());
    }

    protected UserResponse toResponse(UserBean bean) {
        UserResponse response = new UserResponse();
        response.id = bean.id;
        response.username = bean.username;
        response.email = bean.email;
        response.phone = bean.phone;
        response.realName = bean.realName;
        response.sex = bean.sex;
        response.birthday = bean.birthday;
        response.cardNumber = bean.cardNumber;
        response.status = bean.status;
        response.createTime = bean.createTime;
        response.createUserId = bean.createUserId;
        response.createUserName = bean.createUserName;
        response.updateTime = bean.updateTime;
        response.updateUserId = bean.updateUserId;
        response.updateUserName = bean.updateUserName;
        return response;
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
        if (!UserConst.IS_PASSWORD_ENCODED) {
            return password;
        }
        return DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
    }
}
