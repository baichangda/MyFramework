package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.lib.database.jdbc.service.BaseService;
import cn.bcd.server.business.process.backend.base.support_satoken.anno.NotePermission;
import cn.bcd.server.business.process.backend.base.support_satoken.anno.SaCheckNotePermissions;
import cn.bcd.server.business.process.backend.sys.bean.PermissionBean;
import cn.bcd.server.business.process.backend.sys.define.CommonConst;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Service
public class PermissionService extends BaseService<PermissionBean> implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        initNotePermission(event);
    }

    @Transactional
    public void initNotePermission(ContextRefreshedEvent event) {
        //1、扫描所有已经使用过的 NotePermission
        Map<String, Object> beanMap = event.getApplicationContext().getBeansWithAnnotation(Controller.class);
        Set<NotePermission> permissionSet = new LinkedHashSet<>();
        beanMap.values().forEach(e1 -> {
            Class controllerClass = ClassUtils.getUserClass(e1);
            List<Method> methodList = MethodUtils.getMethodsListWithAnnotation(controllerClass, SaCheckNotePermissions.class);
            methodList.forEach(e2 -> {
                SaCheckNotePermissions requiresNotePermissions = e2.getAnnotation(SaCheckNotePermissions.class);
                permissionSet.addAll(Arrays.stream(requiresNotePermissions.value()).collect(Collectors.toSet()));
            });
        });

        //2、清空权限表
        deleteAll();

        //3、转换成实体类并保存
        List<PermissionBean> permissionBeanList = permissionSet.stream().map(e -> {
            PermissionBean permissionBean = new PermissionBean();
            permissionBean.code=e.code;
            permissionBean.name=e.note;
            return permissionBean;
        }).collect(Collectors.toList());
        insertBatch(permissionBeanList);
    }

    public List<PermissionBean> findPermissionsByUserId(Long userId) {
        if (CommonConst.ADMIN_ID == userId) {
            return list();
        } else {
            String sql = "select d.* from t_sys_user_role a " +
                    "inner join t_sys_role_menu b on b.role_code=a.role_code " +
                    "inner join t_sys_menu_permission c on b.menu_id=c.menu_id " +
                    "inner join t_sys_permission d on c.permission_code=d.code " +
                    "where a.user_id= ?";
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(PermissionBean.class), userId);
        }
    }

    public List<PermissionBean> findPermissionsByUsername(String username) {
        if (CommonConst.ADMIN_USERNAME.equals(username)) {
            return list();
        } else {
            String sql= """
                    select d.* from t_sys_user x
                    inner join t_sys_user_role a on x.id=a.user_id
                    inner join t_sys_role_menu b on b.role_code=a.role_code
                    inner join t_sys_menu_permission c on b.menu_id=c.menu_id
                    inner join t_sys_permission d on c.permission_code=d.code
                    where x.username= ?
                    """;
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(PermissionBean.class), username);
        }
    }

}
