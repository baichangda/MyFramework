package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.lib.database.jdbc.service.BaseService;
import cn.bcd.server.business.process.backend.sys.bean.RoleBean;
import cn.bcd.server.business.process.backend.sys.define.CommonConst;
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
        if (CommonConst.ADMIN_ID == userId) {
            return list();
        } else {
            String sql = """
                    select b.* from t_sys_user_role a
                    inner join t_sys_role b on a.role_code=b.code
                    where a.user_id=?
                    """;
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(RoleBean.class), userId);
        }
    }

    public List<RoleBean> findRolesByUsername(String username) {
        if (CommonConst.ADMIN_USERNAME.equals(username)) {
            return list();
        } else {
            String sql = """
                    select b.* from t_sys_user x
                    inner join t_sys_user_role a on x.id=a.user_id
                    inner join t_sys_role b on a.role_code=b.code
                    where x.username=?
                    """;
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(RoleBean.class), username);
        }
    }
}
