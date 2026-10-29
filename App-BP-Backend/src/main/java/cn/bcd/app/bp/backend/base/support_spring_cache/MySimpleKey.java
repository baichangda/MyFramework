package cn.bcd.app.bp.backend.base.support_spring_cache;

import cn.bcd.lib.base.json.JsonUtil;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public class MySimpleKey implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String digest;

    public MySimpleKey(String className, String methodName, Object... args) {
        this(className, methodName, argumentTypes(args), args);
    }

    public MySimpleKey(String className, Method method, Object... args) {
        this(className, method.getName(),
                Arrays.stream(method.getParameterTypes()).map(Class::getName).toArray(String[]::new), args);
    }

    private MySimpleKey(String className, String methodName, String[] parameterTypes, Object[] args) {
        byte[] json = JsonUtil.toJsonAsBytes(new Object[]{className, methodName, parameterTypes, argumentTypes(args), args});
        try {
            this.digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String[] argumentTypes(Object[] args) {
        return Arrays.stream(args).map(arg -> arg == null ? null : arg.getClass().getName()).toArray(String[]::new);
    }


    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof MySimpleKey key && digest.equals(key.digest);
    }

    @Override
    public final int hashCode() {
        return digest.hashCode();
    }

    @Override
    public String toString() {
        return digest;
    }
}
