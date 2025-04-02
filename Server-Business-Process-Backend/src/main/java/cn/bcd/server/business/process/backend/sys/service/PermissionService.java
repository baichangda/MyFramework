package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.database.jdbc.service.BaseService;
import cn.bcd.server.business.process.backend.base.support_satoken.anno.SaCheckRequestMappingUrl;
import cn.bcd.server.business.process.backend.sys.define.CommonConst;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import cn.bcd.server.business.process.backend.sys.bean.PermissionBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PermissionService extends BaseService<PermissionBean> implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ((PermissionService) getProxy()).init_SaCheckRequestMappingUrl(event);
    }

    @Transactional
    public void init_SaCheckRequestMappingUrl(ContextRefreshedEvent event) {
        //1、扫描所有已经使用过的 NotePermission
        Map<String, Object> beanMap = event.getApplicationContext().getBeansWithAnnotation(Controller.class);
        List<PermissionBean> permissionBeanList = new ArrayList<>();
        beanMap.values().forEach(e1 -> {
            Class<?> controllerClass = ClassUtils.getUserClass(e1);
            RequestMapping controllerRequestMapping = controllerClass.getAnnotation(RequestMapping.class);
            String controllerUrl;
            if (controllerRequestMapping == null) {
                controllerUrl = "";
            } else {
                controllerUrl = controllerRequestMapping.value()[0];
            }
            List<Method> methodList = MethodUtils.getMethodsListWithAnnotation(controllerClass, SaCheckRequestMappingUrl.class);
            methodList.forEach(e2 -> {
                RequestMapping methodRequestMapping = e2.getAnnotation(RequestMapping.class);
                String methodUrl = methodRequestMapping.value()[0];
                SaCheckRequestMappingUrl saCheckRequestMappingUrl = e2.getAnnotation(SaCheckRequestMappingUrl.class);
                PermissionBean permissionBean = new PermissionBean();
                String name = saCheckRequestMappingUrl.value();
                if (name.isEmpty()) {
                    Operation operation = e2.getAnnotation(Operation.class);
                    if (operation != null) {
                        name = operation.summary();
                    }
                }
                permissionBean.name = name;
                permissionBean.resource = Const.uri_prefix_business_process_backend + controllerUrl + methodUrl;
                permissionBeanList.add(permissionBean);
            });
        });
        //2、清空权限表
        deleteAll();
        //3、保存
        insertBatch(permissionBeanList);
    }

    public List<PermissionBean> findPermissionsByUserId(Long userId) {
        if (CommonConst.ADMIN_ID == userId) {
            return list();
        } else {
            String sql = """
                    select d.* from t_sys_user x
                    inner join t_sys_user_role a on x.id=a.user_id
                    inner join t_sys_role_menu b on b.role_id=a.role_id
                    inner join t_sys_menu_permission c on b.menu_id=c.menu_id
                    inner join t_sys_permission d on c.permission_id=d.id
                    where x.username= ?
                    """;
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(PermissionBean.class), userId);
        }
    }

    public List<PermissionBean> findPermissionsByUsername(String username) {
        if (CommonConst.ADMIN_USERNAME.equals(username)) {
            return list();
        } else {
            String sql = """
                    select d.* from t_sys_user x
                    inner join t_sys_user_role a on x.id=a.user_id
                    inner join t_sys_role_menu b on b.role_id=a.role_id
                    inner join t_sys_menu_permission c on b.menu_id=c.menu_id
                    inner join t_sys_permission d on c.permission_id=d.id
                    where x.username= ?
                    """;
            return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(PermissionBean.class), username);
        }
    }
}
