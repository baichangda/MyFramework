package cn.bcd.server.business.backend.process.base.support_jdbc.service;

import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.server.business.backend.process.base.support_jdbc.anno.Unique;

import java.lang.reflect.Field;

public class FieldInfo {
    public final String fieldName;
    public final String columnName;

    public final Field field;

    public final String uniqueMsg;

    public FieldInfo(Field field) {
        this.field = field;
        this.fieldName = field.getName();
        this.columnName = StringUtil.camelCaseToSplitChar(this.fieldName, '_');
        Unique unique = field.getAnnotation(Unique.class);
        if (unique == null) {
            this.uniqueMsg = null;
        } else {
            this.uniqueMsg = unique.msg();
        }
    }
}