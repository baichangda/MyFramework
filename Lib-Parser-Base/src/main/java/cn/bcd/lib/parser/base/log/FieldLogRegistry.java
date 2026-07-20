package cn.bcd.lib.parser.base.log;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FieldLogRegistry {
    private static final Map<Class<? extends Annotation>, FieldLog<?>> FIELD_LOGS = create();

    private FieldLogRegistry() {
    }

    public static FieldLog<?> get(Class<? extends Annotation> annotationClass) {
        return FIELD_LOGS.get(annotationClass);
    }

    public static Map<Class<? extends Annotation>, FieldLog<?>> all() {
        return FIELD_LOGS;
    }

    private static Map<Class<? extends Annotation>, FieldLog<?>> create() {
        Map<Class<? extends Annotation>, FieldLog<?>> map = new LinkedHashMap<>();
        register(map, new FieldLog__F_bean());
        register(map, new FieldLog__F_bean_list());
        register(map, new FieldLog__F_bit_num());
        register(map, new FieldLog__F_bit_num_array());
        register(map, new FieldLog__F_bit_num_easy());
        register(map, new FieldLog__F_customize());
        register(map, new FieldLog__F_date_bcd());
        register(map, new FieldLog__F_date_bytes_6());
        register(map, new FieldLog__F_date_bytes_7());
        register(map, new FieldLog__F_date_ts());
        register(map, new FieldLog__F_num());
        register(map, new FieldLog__F_num_array());
        register(map, new FieldLog__F_string());
        register(map, new FieldLog__F_string_bcd());
        register(map, new FieldLog__F_skip());
        return Map.copyOf(map);
    }

    private static void register(Map<Class<? extends Annotation>, FieldLog<?>> map, FieldLog<?> fieldLog) {
        map.put(fieldLog.annotationClass(), fieldLog);
    }
}
