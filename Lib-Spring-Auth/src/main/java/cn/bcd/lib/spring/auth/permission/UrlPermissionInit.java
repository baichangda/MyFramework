package cn.bcd.lib.spring.auth.permission;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UrlPermissionInit {
    private UrlPermissionInit() {
    }

    public static List<PermissionData> scanUrlPermission(ApplicationContext context) {
        List<PermissionData> permissionDataList = new ArrayList<>();
        Map<String, Object> map = context.getBeansWithAnnotation(Controller.class);
        for (Object obj : map.values()) {
            Class<?> controllerClass = ClassUtils.getUserClass(obj);
            List<Method> methodList = MethodUtils.getMethodsListWithAnnotation(controllerClass, UrlPermission.class);
            if (methodList.isEmpty()) {
                continue;
            }
            RequestMapping controllerRequestMapping = controllerClass.getAnnotation(RequestMapping.class);
            String controllerUrl;
            if (controllerRequestMapping == null) {
                controllerUrl = "";
            } else {
                controllerUrl = controllerRequestMapping.value()[0];
            }
            for (Method method : methodList) {
                UrlPermission urlPermission = method.getAnnotation(UrlPermission.class);
                RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
                if (methodRequestMapping == null) {
                    continue;
                }
                String methodUrl = methodRequestMapping.value()[0];
                permissionDataList.add(new PermissionData(urlPermission.value(), controllerUrl + methodUrl));
            }
        }
        return permissionDataList;
    }
}
