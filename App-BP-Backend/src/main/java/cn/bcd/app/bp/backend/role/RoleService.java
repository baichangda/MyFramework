package cn.bcd.app.bp.backend.role;

import cn.bcd.lib.spring.database.jdbc.service.BaseService;
import cn.bcd.app.bp.backend.user.UserConst;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by Administrator on 2017/4/11.
 */
@Service
public class RoleService extends BaseService<RoleBean> {
    @Autowired
    JdbcTemplate jdbcTemplate;

    public List<RoleBean> findRolesByUserId(Long userId) {
        if (UserConst.ADMIN_ID == userId) {
            return list();
        } else {
            String sql = """
                    select b.* from t_sys_user_role a
                    inner join t_sys_role b on a.role_id=b.id
                    where a.user_id=?
                    """;
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(RoleBean.class), userId);
        }
    }

    public List<RoleBean> findRolesByUsername(String username) {
        if (UserConst.ADMIN_USERNAME.equals(username)) {
            return list();
        } else {
            String sql = """
                    select b.* from t_sys_user x
                    inner join t_sys_user_role a on x.id=a.user_id
                    inner join t_sys_role b on a.role_id=b.id
                    where x.username=?
                    """;
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(RoleBean.class), username);
        }
    }
}
