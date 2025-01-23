package cn.bcd.business.backend.sys.service;

import cn.bcd.business.backend.base.support_jdbc.service.BaseService;
import cn.bcd.business.backend.sys.bean.RoleBean;
import cn.bcd.business.backend.sys.define.CommonConst;
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

    public List<RoleBean> findRolesByUserId(Long userId){
        if (CommonConst.ADMIN_ID == userId) {
            return list();
        }else {
            String sql = "select b.* from t_sys_user_role a inner join t_sys_role b on a.role_code=b.code where a.user_id=?";
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(RoleBean.class), userId);
        }
    }
}
