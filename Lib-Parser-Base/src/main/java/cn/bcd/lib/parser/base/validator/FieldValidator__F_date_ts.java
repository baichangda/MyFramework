package cn.bcd.lib.parser.base.validator;

import cn.bcd.lib.parser.base.anno.F_date_ts;

import java.lang.reflect.Field;

public final class FieldValidator__F_date_ts {
    private FieldValidator__F_date_ts() {
    }

    public static void validate(Field field, F_date_ts annotation) {
        ValidatorUtil.validateDateField(field, "@F_date_ts");
        ValidatorUtil.validateZoneId(field, "@F_date_ts", "valueZoneId", annotation.valueZoneId());
        ValidatorUtil.validateDateFormat(field, "@F_date_ts", annotation.stringFormat());
    }
}
