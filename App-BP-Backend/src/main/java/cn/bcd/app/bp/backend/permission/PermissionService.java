package cn.bcd.app.bp.backend.permission;

import cn.bcd.app.bp.backend.user.UserConst;
import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.NumberCondition;
import cn.bcd.lib.spring.database.common.condition.impl.StringCondition;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionService extends BaseService<PermissionBean>{
    private final JdbcTemplate jdbcTemplate;

    public PermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PermissionBean> findPermissionsByUserId(Long userId) {
        if (UserConst.ADMIN_ID == userId) {
            return list();
        }
        return jdbcTemplate.query(USER_PERMISSION_SQL + " where ur.user_id = ?",
                new BeanPropertyRowMapper<>(PermissionBean.class), userId);
    }

    public List<PermissionBean> findPermissionsByUsername(String username) {
        if (UserConst.ADMIN_USERNAME.equals(username)) {
            return list();
        }
        return jdbcTemplate.query(USER_PERMISSION_SQL
                        + " inner join t_sys_user u on u.id = ur.user_id where u.username = ?",
                new BeanPropertyRowMapper<>(PermissionBean.class), username);
    }

    private static final String USER_PERMISSION_SQL = """
            select distinct p.* from t_sys_user_role ur
            inner join t_sys_role_menu rm on rm.role_id = ur.role_id
            inner join t_sys_menu_permission mp on mp.menu_id = rm.menu_id
            inner join t_sys_permission p on p.id = mp.permission_id
            """;

    public List<PermissionResponse> listPermission(PermissionQueryRequest query) {
        return list(buildCondition(query)).stream()
                .map(this::toResponse).toList();
    }

    public Page<PermissionResponse> pagePermission(
            PermissionQueryRequest query, int pageNum, int pageSize) {
        return page(buildCondition(query), PageRequest.of(pageNum - 1, pageSize)).map(this::toResponse);
    }

    protected Condition buildCondition(PermissionQueryRequest query) {
        return Condition.and(
                NumberCondition.EQUAL("id", query.id),
                StringCondition.EQUAL("name", query.name),
                StringCondition.EQUAL("resource", query.resource)
        );
    }

    public void savePermission(PermissionSaveRequest request) {
        if (request.id == null) {
            PermissionBean bean = new PermissionBean();
            bean.name = request.name;
            bean.resource = request.resource;
            // 在此补充服务端默认值和新增业务规则。
            insert(bean);
        } else {
            if (get(request.id) == null) throw new IllegalArgumentException("Record not found");
            // 在此补充操作权限和修改业务规则。
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("name", request.name);
            changes.put("resource", request.resource);
            update(request.id, changes);
        }
    }

    public void deletePermission(long[] ids) {
        // 在此补充操作权限和关联数据检查。
        delete(Arrays.stream(ids).distinct().toArray());
    }

    protected PermissionResponse toResponse(PermissionBean bean) {
        PermissionResponse response = new PermissionResponse();
        response.id = bean.id;
        response.name = bean.name;
        response.resource = bean.resource;
        response.createTime = bean.createTime;
        response.createUserId = bean.createUserId;
        response.createUserName = bean.createUserName;
        response.updateTime = bean.updateTime;
        response.updateUserId = bean.updateUserId;
        response.updateUserName = bean.updateUserName;
        return response;
    }
}
