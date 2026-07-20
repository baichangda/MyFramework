package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_date_bcd;

import java.lang.reflect.Field;

public final class FieldValidator__F_date_bcd {
    private FieldValidator__F_date_bcd() {
    }

    public static void validate(Field field, F_date_bcd annotation) {
        ValidatorUtil.validateDateField(field, "@F_date_bcd");
        ValidatorUtil.validateZoneId(field, "@F_date_bcd", "zoneId", annotation.zoneId());
        ValidatorUtil.validateZoneId(field, "@F_date_bcd", "valueZoneId", annotation.valueZoneId());
        ValidatorUtil.validateDateFormat(field, "@F_date_bcd", annotation.stringFormat());
    }
}
