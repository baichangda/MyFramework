package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * 此类为日期帮助类(专属于某个时区)
 * 方法都参见
 *
 * @see DateUtil
 * <p>
 * 所有的操作方法都基于某个时区
 */
public class DateZoneUtil {

    /**
     * 注意
     * {@link DateTimeFormatter#withZone(ZoneId)}如果不设置时区
     * 则不能格式化和解析不带时区的日期类、例如{@link Instant}
     */
    public final static ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    public final static ZoneOffset ZONE_OFFSET = ZoneOffset.of("+8");

    public final static DateTimeFormatter FORMATTER_yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZONE_OFFSET);
    public final static DateTimeFormatter FORMATTER_yyyyMMddHHmmss = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZONE_OFFSET);
    public final static DateTimeFormatter FORMATTER_yyyyMMddHHmmssSSS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZONE_OFFSET);
    public final static DateTimeFormatter FORMATTER_yyyy_MM_dd = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZONE_OFFSET);
    public final static DateTimeFormatter FORMATTER_yyyy_MM_dd_HH_mm_ss = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE_OFFSET);

    /**
     * 根据dateStr长度转换成不同的时间
     *
     * @param dateStr
     * @return
     */
    public static Date stringToDate(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        int len = dateStr.length();
        return switch (len) {
            case 8 -> DateZoneUtil.strToDate_yyyyMMdd(dateStr);
            case 14 -> DateZoneUtil.strToDate_yyyyMMddHHmmss(dateStr);
            case 17 -> DateZoneUtil.strToDate_yyyyMMddHHmmssSSS(dateStr);
            default -> throw BaseException.get("dateStr[{}] not support", dateStr);
        };
    }

    public static LocalDateTime strToLdt_yyyyMMdd(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return LocalDate.from(FORMATTER_yyyyMMdd.parse(dateStr)).atStartOfDay();
    }

    public static Date strToDate_yyyyMMdd(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return Date.from(LocalDate.from(FORMATTER_yyyyMMdd.parse(dateStr)).atStartOfDay().toInstant(ZONE_OFFSET));
    }

    public static LocalDateTime strToLdt_yyyyMMddHHmmss(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return LocalDateTime.from(FORMATTER_yyyyMMddHHmmss.parse(dateStr));
    }

    public static Date strToDate_yyyyMMddHHmmss(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return Date.from(Instant.from(FORMATTER_yyyyMMddHHmmss.parse(dateStr)));
    }


    public static LocalDateTime strToLdt_yyyyMMddHHmmssSSS(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return LocalDateTime.from(FORMATTER_yyyyMMddHHmmssSSS.parse(dateStr));
    }

    public static Date strToDate_yyyyMMddHHmmssSSS(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return Date.from(Instant.from(FORMATTER_yyyyMMddHHmmssSSS.parse(dateStr)));
    }

    public static LocalDateTime strToLdt_yyyy_MM_dd_HH_mm_ss(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return LocalDateTime.from(FORMATTER_yyyy_MM_dd_HH_mm_ss.parse(dateStr));
    }

    public static Date strToDate_yyyy_MM_dd_HH_mm_ss(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return Date.from(Instant.from(FORMATTER_yyyy_MM_dd_HH_mm_ss.parse(dateStr)));
    }

    public static Date strToDate_yyyy_MM_dd(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        return Date.from(LocalDate.from(FORMATTER_yyyy_MM_dd.parse(dateStr)).atStartOfDay().toInstant(ZONE_OFFSET));
    }

    public static String ldtToStr_yyyyMMdd(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return FORMATTER_yyyyMMdd.format(ldt);
    }

    public static String dateToStr_yyyyMMdd(Date date) {
        if (date == null) {
            return null;
        }
        return FORMATTER_yyyyMMdd.format(date.toInstant());
    }

    public static String ldtToStr_yyyyMMddHHmmss(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return FORMATTER_yyyyMMddHHmmss.format(ldt);
    }

    public static String dateToStr_yyyyMMddHHmmss(Date date) {
        if (date == null) {
            return null;
        }
        return FORMATTER_yyyyMMddHHmmss.format(date.toInstant());
    }

    public static String ldtToStr_yyyyMMddHHmmssSSS(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return FORMATTER_yyyyMMddHHmmssSSS.format(ldt);
    }

    public static String dateToStr_yyyyMMddHHmmssSSS(Date date) {
        if (date == null) {
            return null;
        }
        return FORMATTER_yyyyMMddHHmmssSSS.format(date.toInstant());
    }

    public static String ldtToStr_yyyy_MM_dd(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return FORMATTER_yyyy_MM_dd.format(ldt);
    }


    public static String dateToStr_yyyy_MM_dd(Date date) {
        if (date == null) {
            return null;
        }
        return FORMATTER_yyyy_MM_dd.format(date.toInstant());
    }

    public static String ldtToStr_yyyy_MM_dd_HH_mm_ss(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return FORMATTER_yyyy_MM_dd_HH_mm_ss.format(ldt);
    }

    public static String dateToStr_yyyy_MM_dd_HH_mm_ss(Date date) {
        if (date == null) {
            return null;
        }
        return FORMATTER_yyyy_MM_dd_HH_mm_ss.format(date.toInstant());
    }


    /**
     * @param date
     * @param unit
     * @return
     * @see DateUtil#getFloorDate(Date, ChronoUnit, ZoneOffset)
     */
    public static Date getFloorDate(Date date, ChronoUnit unit) {
        return DateUtil.getFloorDate(date, unit, ZONE_OFFSET);
    }

    /**
     * @param date
     * @param unit
     * @return
     * @see DateUtil#getCeilDate(Date, ChronoUnit, ZoneOffset)
     */
    public static Date getCeilDate(Date date, ChronoUnit unit) {
        return DateUtil.getCeilDate(date, unit, ZONE_OFFSET);
    }

    /**
     * @param startDate
     * @param endDate
     * @param skip
     * @param unit
     * @return
     * @see DateUtil#range(Date, Date, int, ChronoUnit, ZoneOffset)
     */
    public static List<Date[]> range(Date startDate, Date endDate, int skip, ChronoUnit unit) {
        return DateUtil.range(startDate, endDate, skip, unit, ZONE_OFFSET);
    }


    public static void main(String[] args) {
        Date time = strToDate_yyyyMMdd("20111111");
        System.out.println(time);
        System.out.println(dateToStr_yyyyMMdd(time));
        System.out.println(dateToStr_yyyyMMddHHmmss(time));
//
//
//        DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern(DateUtil.DATE_FORMAT_DAY).withZone(ZONE_OFFSET);
//        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern(DateUtil.DATE_FORMAT_SECOND).withZone(ZONE_OFFSET);
//        System.out.println(LocalDate.from(formatter1.parse("20111111")).atTime(LocalTime.MIN).toInstant(ZONE_OFFSET).toEpochMilli() / 1000);
//        System.out.println(Instant.from(formatter2.parse("20111111000000")).toEpochMilli() / 1000);
//
//        Date d1 = new Date();
//        Date d2 = new Date();
//        formatDateParam(d1, d2);
//        System.out.println(d1);
//        System.out.println(d2);
//
//        Date newD1 = getFloorDate(d1, ChronoUnit.HOURS);
//        System.out.println(d1.getTime());
//        System.out.println(newD1.getTime());
//        System.out.println(DateUtil.getDiff(d1, newD1, ChronoUnit.SECONDS, true));

        LocalDateTime ldt1 = LocalDateTime.of(2023, 2, 1, 1, 1, 1);
        LocalDateTime ldt2 = LocalDateTime.of(2023, 8, 25, 1, 20, 1);
        Duration between = Duration.between(ldt1, ldt2);
        System.out.println(between.toDays());
        System.out.println(between.toHours());
        System.out.println(ChronoUnit.DAYS.between(ldt1, ldt2));
        System.out.println(ChronoUnit.MONTHS.between(ldt1, ldt2));
    }
}
