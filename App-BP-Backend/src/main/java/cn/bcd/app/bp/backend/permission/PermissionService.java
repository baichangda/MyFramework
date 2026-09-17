package cn.bcd.app.bp.backend.permission;

import cn.bcd.app.bp.backend.user.UserConst;
import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
