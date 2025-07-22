package cn.bcd.app.business.process.backend.base.support_satoken;

import cn.bcd.app.business.process.backend.base.support_satoken.anno.SaCheckAction;
import cn.bcd.app.business.process.backend.base.support_satoken.anno.SaCheckRequestMappingUrl;
import cn.dev33.satoken.annotation.handler.SaAnnotationHandlerInterface;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("unchecked")
@Configuration
public class SaTokenConfig implements WebMvcConfigurer, ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /**
         * 开启了
         * 1、/** 的注解鉴权、即所有controller接口都会被注解鉴权
         * 2、路由匹配的登陆验证
         * 具体逻辑查看 {@link  SaInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}
         */
//        registry.addInterceptor(new SaInterceptor(handler -> {
//            StpUtil.checkLogin();
//        })).addPathPatterns("/api/**").excludePathPatterns("/api/sys/user/login", "/api/anno");
    }

    /**
     * 自定义注解
     */
    private void registerSaAnnotationHandler() {
        SaAnnotationStrategy.instance.registerAnnotationHandler(new SaAnnotationHandlerInterface<SaCheckAction>() {
            @Override
            public Class<SaCheckAction> getHandlerAnnotationClass() {
                return SaCheckAction.class;
            }

            @Override
            public void checkMethod(SaCheckAction at, AnnotatedElement element) {
                Method method = (Method) element;
                final String className = method.getDeclaringClass().getName();
                final String methodName = method.getName();
                StpUtil.checkPermissionAnd(className + ":" + methodName);

            }
        });

        SaAnnotationStrategy.instance.registerAnnotationHandler(new SaAnnotationHandlerInterface<SaCheckRequestMappingUrl>() {
            @Override
            public Class<SaCheckRequestMappingUrl> getHandlerAnnotationClass() {
                return SaCheckRequestMappingUrl.class;
            }

            @Override
            public void checkMethod(SaCheckRequestMappingUrl at, AnnotatedElement element) {
                Method method = (Method) element;
                Class<?> clazz = method.getDeclaringClass();
                RequestMapping classRequestMapping = clazz.getAnnotation(RequestMapping.class);
                RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
                String[] classUrls = classRequestMapping.value();
                String[] methodUrls = methodRequestMapping.value();
                Set<String> permissionSet = new HashSet<>();
                Arrays.stream(classUrls).forEach(e1 -> Arrays.stream(methodUrls).forEach(e2 -> permissionSet.add(e1 + e2)));
                StpUtil.checkPermissionAnd(permissionSet.toArray(new String[0]));
            }
        });
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        registerSaAnnotationHandler();
    }
}
