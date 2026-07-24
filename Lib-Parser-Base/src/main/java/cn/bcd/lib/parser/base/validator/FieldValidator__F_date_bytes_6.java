package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_date_bytes_6;

import java.lang.reflect.Field;

public final class FieldValidator__F_date_bytes_6 {
    private FieldValidator__F_date_bytes_6() {
    }

    public static void validate(Field field, F_date_bytes_6 annotation) {
        if (field.getType() != int[].class) {
            ValidatorUtil.validateDateField(field, "@F_date_bytes_6");
        }
        ValidatorUtil.validateZoneId(field, "@F_date_bytes_6", "zoneId", annotation.zoneId());
        ValidatorUtil.validateZoneId(field, "@F_date_bytes_6", "valueZoneId", annotation.valueZoneId());
        ValidatorUtil.validateDateFormat(field, "@F_date_bytes_6", annotation.stringFormat());
    }
}
