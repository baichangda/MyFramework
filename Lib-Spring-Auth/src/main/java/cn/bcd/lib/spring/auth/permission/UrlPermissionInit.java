package cn.bcd.lib.spring.auth.permission;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class UrlPermissionInit implements ApplicationListener<ContextRefreshedEvent> {

    static Logger logger = LoggerFactory.getLogger(UrlPermissionInit.class);

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        List<PermissionData> permissions = scanUrlPermission(event);
        logger.info("scan UrlPermission num[{}]", permissions.size());
    }

    public static List<PermissionData> scanUrlPermission(ContextRefreshedEvent event) {
        List<PermissionData> permissionDataList = new ArrayList<>();
        Map<String, Object> map = event.getApplicationContext().getBeansWithAnnotation(Controller.class);
        for (Object obj : map.values()) {
            Class<?> controllerClass = ClassUtils.getUserClass(obj);
            RequestMapping controllerRequestMapping = controllerClass.getAnnotation(RequestMapping.class);
            String controllerUrl;
            if (controllerRequestMapping == null) {
                controllerUrl = "";
            } else {
                controllerUrl = controllerRequestMapping.value()[0];
            }
            List<Method> methodList = MethodUtils.getMethodsListWithAnnotation(controllerClass, UrlPermission.class);
            for (Method method : methodList) {
                UrlPermission urlPermission = method.getAnnotation(UrlPermission.class);
                RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
                if (methodRequestMapping == null) {
                    continue;
                }
                String methodUrl = methodRequestMapping.value()[0];
                PermissionData permissionData = new PermissionData();
                permissionData.code = urlPermission.value();
                permissionData.remark = urlPermission.remark();
                permissionData.resource = controllerUrl + methodUrl;
                permissionDataList.add(permissionData);
            }
        }
        return permissionDataList;
    }

}
