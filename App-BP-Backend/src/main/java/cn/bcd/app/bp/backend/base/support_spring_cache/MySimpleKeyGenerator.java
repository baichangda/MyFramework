package cn.bcd.app.bp.backend.base.support_spring_cache;

import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.util.ClassUtils;
import org.springframework.util.MethodInvoker;

import java.lang.reflect.Method;

public class MySimpleKeyGenerator extends SimpleKeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... params) {
        return generate(target.getClass(), method, params);
    }

    public MySimpleKey generate(Class<?> clazz, Method method, Object... params) {
        return new MySimpleKey(clazz.getName(), method, params);
    }

    public MySimpleKey generate(Object target, String methodName, Object... params) {
        return generate(target.getClass(), methodName, params);
    }

    public MySimpleKey generate(String className, String methodName, Object... params) {
        return generate(ClassUtils.resolveClassName(className, ClassUtils.getDefaultClassLoader()), methodName, params);
    }

    /** For ambiguous overloads (especially null arguments), use the Method overload. */
    public MySimpleKey generate(Class<?> clazz, String methodName, Object... params) {
        MethodInvoker invoker = new MethodInvoker();
        invoker.setTargetClass(clazz);
        invoker.setTargetMethod(methodName);
        invoker.setArguments(params);
        try {
            invoker.prepare();
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot resolve cache method " + clazz.getName() + "." + methodName, e);
        }
        return generate(clazz, invoker.getPreparedMethod(), params);
    }
}
