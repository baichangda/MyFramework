package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.anno.F_bean;
import cn.bcd.lib.parser.base.anno.F_bean_list;
import cn.bcd.lib.parser.base.util.BeanTypeUtil;
import cn.bcd.lib.parser.base.util.ParseUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks the complete annotated dependency graph before source generation or length calculation. */
public final class BeanDependencyValidator {
    private BeanDependencyValidator() {
    }

    public static void validate(Class<?> type) {
        List<String> path = new ArrayList<>();
        path.add(type.getName());
        visit(type, new HashSet<>(), new HashSet<>(), path);
    }

    private static void visit(Class<?> type, Set<Class<?>> active, Set<Class<?>> completed, List<String> path) {
        if (active.contains(type)) {
            throw BaseException.get("circular bean dependency: {}", String.join(" -> ", path));
        }
        if (completed.contains(type)) {
            return;
        }
        active.add(type);
        try {
            for (Field field : ParseUtil.getParseFields(type)) {
                List<Class<?>> dependencies;
                if (field.isAnnotationPresent(F_bean.class)) {
                    Class<?> target = field.getType();
                    dependencies = target.isInterface() ? BeanTypeUtil.getImplementations(target) : List.of(target);
                } else if (field.isAnnotationPresent(F_bean_list.class)) {
                    dependencies = List.of(FieldValidator__F_bean_list.getElementType(field));
                } else {
                    continue;
                }
                for (Class<?> dependency : dependencies) {
                    path.add(field.getDeclaringClass().getName() + "." + field.getName() + "[" + dependency.getName() + "]");
                    try {
                        visit(dependency, active, completed, path);
                    } finally {
                        path.removeLast();
                    }
                }
            }
            completed.add(type);
        } finally {
            active.remove(type);
        }
    }
}
