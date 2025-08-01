package cn.bcd.lib.parser.base.anno;

import cn.bcd.lib.parser.base.data.ByteOrder;
import cn.bcd.lib.parser.base.data.DateTsMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于解析的原始数据表示为时间戳
 * 毫秒、秒都可以
 *
 * 适用于如下类型
 * {@link java.util.Date}
 * {@link java.time.Instant}
 * {@link java.time.LocalDateTime}
 * {@link java.time.OffsetDateTime}
 * {@link java.time.ZonedDateTime}
 * int 此时代表时间戳秒
 * long 此时代表时间戳毫秒
 * {@link String} 此时使用{@link #stringFormat()}、{@link #valueZoneId()}格式化
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface F_date_ts {
    /**
     * 1 协议定义uint64、代表时间戳毫秒
     * 2 协议定义uint64、代表时间戳秒
     * 3 协议定义uint32、代表时间戳秒
     * 4 协议定义float64、代表时间戳毫秒
     * 5 协议定义float64、代表秒、精度为0.001、小数位代表毫秒
     */
    DateTsMode mode();


    /**
     * 字节序模式
     */
    ByteOrder order() default ByteOrder.Default;

    /**
     * 转换日期为字符串的格式
     * 在字段类型如下时候会使用到
     * {@link String}
     */
    String stringFormat() default "yyyyMMddHHmmssSSS";

    /**
     * 转换为字段值需要用到的时区
     * 在字段类型如下时候会使用到
     * {@link java.time.LocalDateTime}
     * {@link java.time.OffsetDateTime}
     * {@link java.time.ZonedDateTime}
     * {@link String}
     * 可以为时区偏移量、或者时区id、例如中国时区
     * 时区偏移量为 +8
     * 时区id为 Asia/Shanghai
     * 区别是时区id是考虑了夏令时的、优先使用时区偏移量、效率较高
     */
    String valueZoneId() default "+8";

}
