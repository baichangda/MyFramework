package cn.bcd.app.bp.backend.base.support_spring_paramconverter;

import cn.bcd.lib.base.util.DateUtil;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 设别如下几种date参数
 * 1、毫秒时间戳、example: source=1611629450000
 * 2、日期类型字符串
 * 此时日期格式有3种
 * {@link DateUtil#FORMATTER_yyyyMMdd}、
 * {@link DateUtil#FORMATTER_yyyyMMddHHmmss}
 * {@link DateUtil#FORMATTER_yyyyMMddHHmmssSSS}
 * 字符串必须以s开头
 * example: s20210126、s20210126111111、s20210126111111111
 */
@Component
public class StringToDateConverter implements Converter<String, Date> {

    @Override
    public Date convert(String source) {
        if (source.isEmpty()) {
            return null;
        } else {
            if (source.charAt(0) == 's') {
                return DateUtil.stringToDate(source.substring(1));
            } else {
                long t = Long.parseLong(source);
                return new Date(t);
            }
        }
    }
}
