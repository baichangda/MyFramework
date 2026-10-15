package cn.bcd.lib.parser.base.util;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.ClassUtil;
import cn.bcd.lib.parser.base.anno.C_impl;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves explicitly registered implementations and rejects ambiguous dispatch values. */
public final class BeanTypeUtil {
    private BeanTypeUtil() {
    }

    private static final ClassValue<List<Class<?>>> IMPLEMENTATIONS = new ClassValue<>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> type) {
            try {
                List<Class<?>> implementations = ClassUtil.getClassesByParentClass(type, type.getPackageName()).stream()
                        .filter(implementation -> implementation.getDeclaredAnnotation(C_impl.class) != null)
                        .sorted(Comparator.comparing(Class::getName))
                        .toList();
                Map<Integer, Class<?>> values = new HashMap<>();
                for (Class<?> implementation : implementations) {
                    for (int value : implementation.getDeclaredAnnotation(C_impl.class).value()) {
                        Class<?> previous = values.putIfAbsent(value, implementation);
                        if (previous != null) {
                            throw BaseException.get("interface[{}] duplicate C_impl {} between class[{}] and class[{}]",
                                    type.getName(), value == C_impl.Default ? "default" : "value[" + value + "]",
                                    previous.getName(), implementation.getName());
                        }
                    }
                }
                return implementations;
            } catch (IOException | ClassNotFoundException exception) {
                throw BaseException.get(exception);
            }
        }
    };

    public static List<Class<?>> getImplementations(Class<?> type) {
        return IMPLEMENTATIONS.get(type);
    }
}
