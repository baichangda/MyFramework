package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DateUtil {


    public final static ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    public final static ZoneOffset ZONE_OFFSET = ZoneOffset.of("+8");

    /**
     * 注意
     * {@link DateTimeFormatter#withZone(ZoneId)}如果不设置时区
     * 则不能格式化和解析不带时区的日期类、例如{@link Instant}
     */
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
            case 8 -> DateUtil.strToDate_yyyyMMdd(dateStr);
            case 14 -> DateUtil.strToDate_yyyyMMddHHmmss(dateStr);
            case 17 -> DateUtil.strToDate_yyyyMMddHHmmssSSS(dateStr);
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
     * 清除毫秒值
     * @param date
     * @return
     */
    public static Date clearMills(Date date) {
        long time = date.getTime();
        return new Date((time / 1000) * 1000);
    }

    public static List<Date[]> range(Date startDate, Date endDate, int skip, ChronoUnit unit) {
        return range(startDate, endDate, skip, unit, ZONE_OFFSET);
    }

    /**
     * 获取开始时间结束时间按照 日期单位 形成多个日期区间
     * 第一个区间开始时间为传入开始时间
     * 最后一个区间结束时间为传入结束时间
     *
     * @param startDate  开始时间、包含
     * @param endDate    结束时间、不包含
     * @param amount     时间区间跨度
     * @param unit       支持
     *                   {@link ChronoUnit#MINUTES}
     *                   {@link ChronoUnit#HOURS}
     *                   {@link ChronoUnit#DAYS}
     *                   {@link ChronoUnit#WEEKS}
     *                   {@link ChronoUnit#MONTHS}
     * @param zoneOffset 时区
     * @return 每一个数组第一个为开始时间, 第二个为结束时间
     */
    public static List<Date[]> range(Date startDate, Date endDate, int amount, ChronoUnit unit, ZoneOffset zoneOffset) {
        List<Date[]> returnList = new ArrayList<>();
        if (amount <= 0) {
            throw BaseException.get("amount[{}] must be greater than 0", amount);
        }
        if (startDate.equals(endDate)) {
            return returnList;
        }
        if (startDate.after(endDate)) {
            throw BaseException.get("startDate[{}] must be before endDate[{}]", startDate, endDate);
        }
        LocalDateTime ldt1 = LocalDateTime.ofInstant(startDate.toInstant(), zoneOffset);
        LocalDateTime ldt2 = LocalDateTime.ofInstant(endDate.toInstant(), zoneOffset);
        switch (unit) {
            case MINUTES: {
                LocalDateTime start = ldt1.withSecond(0).withNano(0);
                LocalDateTime end;
                while (true) {
                    end = start.plusMinutes(amount);
                    returnList.add(new Date[]{Date.from(start.toInstant(zoneOffset)), Date.from(end.toInstant(zoneOffset))});
                    if (!end.isBefore(ldt2)) {
                        break;
                    }
                    start = end;
                }
                returnList.getFirst()[0] = Date.from(ldt1.toInstant(zoneOffset));
                returnList.getLast()[1] = Date.from(ldt2.toInstant(zoneOffset));
                break;
            }
            case HOURS: {
                LocalDateTime start = ldt1.withMinute(0).withSecond(0).withNano(0);
                LocalDateTime end;
                while (true) {
                    end = start.plusHours(amount);
                    returnList.add(new Date[]{Date.from(start.toInstant(zoneOffset)), Date.from(end.toInstant(zoneOffset))});
                    if (!end.isBefore(ldt2)) {
                        break;
                    }
                    start = end;
                }
                returnList.getFirst()[0] = Date.from(ldt1.toInstant(zoneOffset));
                returnList.getLast()[1] = Date.from(ldt2.toInstant(zoneOffset));
                break;
            }
            case DAYS: {
                LocalDateTime start = ldt1.withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime end;
                while (true) {
                    end = start.plusDays(amount);
                    returnList.add(new Date[]{Date.from(start.toInstant(zoneOffset)), Date.from(end.toInstant(zoneOffset))});
                    if (!end.isBefore(ldt2)) {
                        break;
                    }
                    start = end;
                }
                returnList.getFirst()[0] = Date.from(ldt1.toInstant(zoneOffset));
                returnList.getLast()[1] = Date.from(ldt2.toInstant(zoneOffset));
                break;
            }
            case WEEKS: {
                int dayOfWeek = ldt1.get(ChronoField.DAY_OF_WEEK);
                LocalDateTime start = ldt1.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1 - dayOfWeek);
                LocalDateTime end;
                while (true) {
                    end = start.plusWeeks(amount);
                    returnList.add(new Date[]{Date.from(start.toInstant(zoneOffset)), Date.from(end.toInstant(zoneOffset))});
                    if (!end.isBefore(ldt2)) {
                        break;
                    }
                    start = end;
                }
                returnList.getFirst()[0] = Date.from(ldt1.toInstant(zoneOffset));
                returnList.getLast()[1] = Date.from(ldt2.toInstant(zoneOffset));
                break;
            }
            case MONTHS: {
                LocalDateTime start = ldt1.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime end;
                while (true) {
                    end = start.plusMonths(amount);
                    returnList.add(new Date[]{Date.from(start.toInstant(zoneOffset)), Date.from(end.toInstant(zoneOffset))});
                    if (!end.isBefore(ldt2)) {
                        break;
                    }
                    start = end;
                }
                returnList.getFirst()[0] = Date.from(ldt1.toInstant(zoneOffset));
                returnList.getLast()[1] = Date.from(ldt2.toInstant(zoneOffset));
                break;
            }
            default: {
                throw BaseException.get("unit[{}] Not Support!", unit.toString());
            }
        }
        return returnList;
    }

    /**
     * 计算两个时间相差多少日期单位
     * 如果不足一个单位、则参考up参数、up=true则视为加1的日期单位、否则忽略
     *
     * @param d1   开始时间
     * @param d2   结束时间
     * @param unit 支持
     *             {@link ChronoUnit#MILLIS}
     *             {@link ChronoUnit#SECONDS}
     *             {@link ChronoUnit#MINUTES}
     *             {@link ChronoUnit#HOURS}
     *             {@link ChronoUnit#DAYS}
     * @param up   如果存在小数位,是向上取整还是向下取整;true代表向上;false代表向下
     * @return 相差日期单位数
     */
    public static long getDiff(Date d1, Date d2, ChronoUnit unit, boolean up) {
        long unitMillis;
        switch (unit) {
            case DAYS: {
                unitMillis = ChronoUnit.DAYS.getDuration().toMillis();
                break;
            }
            case HOURS: {
                unitMillis = ChronoUnit.HOURS.getDuration().toMillis();
                break;
            }
            case MINUTES: {
                unitMillis = ChronoUnit.MINUTES.getDuration().toMillis();
                break;
            }
            case SECONDS: {
                unitMillis = ChronoUnit.SECONDS.getDuration().toMillis();
                break;
            }
            case MILLIS: {
                return d2.getTime() - d1.getTime();
            }
            default: {
                throw BaseException.get("[DateUtil.getDiff],unit[{}] Not Support!", unit.toString());
            }
        }
        long begin = d1.getTime();
        long end = d2.getTime();
        long diff = end - begin;
        if (diff > 0) {
            double res = diff / ((double) unitMillis);
            if (up) {
                return (long) Math.ceil(res);
            } else {
                return (long) Math.floor(res);
            }
        } else if (diff < 0) {
            double res = diff / ((double) unitMillis);
            if (up) {
                return -(long) Math.ceil(-res);
            } else {
                return -(long) Math.floor(-res);
            }
        } else {
            return 0;
        }
    }

    /**
     * date转换为时间戳字节数组
     *
     * @param date
     * @return
     */
    public static byte[] dateToBytes(Date date) {
        long ts = date.getTime();
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (ts >> (i * 8));
        }
        return bytes;
    }

    /**
     * bytes时间戳格式转换为date
     *
     * @param bytes
     * @param offset
     * @return
     */
    public static Date bytesToDate(byte[] bytes, int offset) {
        long ts = 0;
        for (int i = 0; i < 8; i++) {
            ts |= (bytes[offset + i] & 0xffL) << (i * 8);
        }
        return new Date(ts);
    }

    /**
     * 添加多个date到bytes前面、得到新的bytes
     *
     * @param bytes
     * @param dates
     * @return
     */
    public static byte[] prependDatesToBytes(byte[] bytes, Date... dates) {
        if (dates.length == 0) {
            return bytes;
        }
        byte[] res = new byte[bytes.length + dates.length * 8];
        for (int i = 0; i < dates.length; i++) {
            byte[] temp = dateToBytes(dates[i]);
            System.arraycopy(temp, 0, res, i * 8, 8);
        }
        System.arraycopy(bytes, 0, res, dates.length * 8, bytes.length);
        return res;
    }

    /**
     * 从bytes中获取多个date
     *
     * @param bytes
     * @param num
     * @return
     */
    public static Date[] getPrependDatesFromBytes(byte[] bytes, int num) {
        Date[] dates = new Date[num];
        for (int i = 0; i < num; i++) {
            dates[i] = bytesToDate(bytes, i * 8);
        }
        return dates;
    }

    /**
     * 缓存方式获取毫秒级时间戳
     * {@link System#currentTimeMillis()}在高频率调用下,性能过低
     * 单线程大概300倍差距、多线程差距更大
     */
    public enum CacheMillisecond {
        instance;
        private volatile long l;

        CacheMillisecond() {
            System.out.println("CacheMillisecond init");
            l = System.currentTimeMillis();
            new ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "CacheMillisecond");
                thread.setDaemon(true);
                return thread;
            }).scheduleAtFixedRate(() -> l = System.currentTimeMillis(),
                    0, 1, TimeUnit.MILLISECONDS);
        }

        public static long current() {
            return instance.l;
        }
    }

    /**
     * 缓存方式获取秒级时间戳
     * {@link System#currentTimeMillis()}在高频率调用下,性能过低
     * 单线程大概300倍差距、多线程差距更大
     */
    public enum CacheSecond {
        instance;
        private volatile long l;

        CacheSecond() {
            System.out.println("CacheSecond init");
            l = System.currentTimeMillis() / 1000L;
            new ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "CacheSecond");
                thread.setDaemon(true);
                return thread;
            }).scheduleAtFixedRate(() -> l = System.currentTimeMillis() / 1000L,
                    0, 1, TimeUnit.SECONDS);
        }

        public static long current() {
            return instance.l;
        }
    }
}
