package cn.bcd.lib.database.mongo.service;

import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.lib.database.mongo.anno.Unique;

import java.lang.reflect.Field;

public class UniqueInfo {
    public final Field field;
    public final String fieldName;
    public final String msg;
    public final int code;

    public UniqueInfo(Field field) {
        Unique unique = field.getAnnotation(Unique.class);
        this.field = field;
        this.fieldName = field.getName();
        this.msg = StringUtil.format(unique.msg(), this.fieldName);
        this.code= unique.code();
    }
}
