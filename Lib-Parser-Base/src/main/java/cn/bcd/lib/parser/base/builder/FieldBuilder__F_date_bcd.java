package cn.bcd.lib.parser.base.builder;

import cn.bcd.lib.parser.base.anno.F_date_bcd;
import cn.bcd.lib.parser.base.util.ParseUtil;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Field;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class FieldBuilder__F_date_bcd extends FieldBuilder {
    @Override
    public void buildParse(BuilderContext context) {
        final StringBuilder body = context.method_body;
        final F_date_bcd anno = context.field.getAnnotation(F_date_bcd.class);
        if (anno.skip()) {
            ParseUtil.appendSkip_parse(6, "", context);
            return;
        }
        final Field field = context.field;
        final String varNameInstance = FieldBuilder.varNameInstance;
        final Class<?> fieldTypeClass = field.getType();
        final String varNameField = ParseUtil.getFieldVarName(context);
        final String varNameLongField = varNameField + "_long";
        final String zoneDateTimeClassName = ZonedDateTime.class.getName();
        final String varNameZoneId = ParseUtil.defineZoneIdClassVar(context, anno.zoneId());
        ParseUtil.append(body, "final long {}={}.read({},{},{});\n", varNameLongField, FieldBuilder__F_date_bcd.class.getName()
                , varNameByteBuf, varNameZoneId, anno.baseYear());
        //根据字段类型格式化
        if (Date.class.isAssignableFrom(fieldTypeClass)) {
            final String dateClassName = Date.class.getName();
            ParseUtil.append(body, "{}.{}=new {}({});\n", varNameInstance, field.getName(), dateClassName, varNameLongField);
        } else if (Instant.class.isAssignableFrom(fieldTypeClass)) {
            ParseUtil.append(body, "{}.{}={}.ofEpochMilli({});\n", varNameInstance, field.getName(), Instant.class.getName(), varNameLongField);
        } else if (LocalDateTime.class.isAssignableFrom(fieldTypeClass)) {
            final String varNameValueZoneId = ParseUtil.defineClassVar(context, ZoneId.class, "{}.of(\"{}\")", ZoneId.class.getName(), anno.valueZoneId());
            ParseUtil.append(body, "{}.{}={}.ofInstant({}.ofEpochMilli({}),{});\n", varNameInstance, field.getName(), LocalDateTime.class.getName(), Instant.class.getName(), varNameLongField, varNameValueZoneId);
        } else if (OffsetDateTime.class.isAssignableFrom(fieldTypeClass)) {
            final String varNameValueZoneId = ParseUtil.defineClassVar(context, ZoneId.class, "{}.of(\"{}\")", ZoneId.class.getName(), anno.valueZoneId());
            ParseUtil.append(body, "{}.{}={}.ofInstant({}.ofEpochMilli({}),{});\n", varNameInstance, field.getName(), OffsetDateTime.class.getName(), Instant.class.getName(), varNameLongField, varNameValueZoneId);
        } else if (ZonedDateTime.class.isAssignableFrom(fieldTypeClass)) {
            final String varNameValueZoneId = ParseUtil.defineClassVar(context, ZoneId.class, "{}.of(\"{}\")", ZoneId.class.getName(), anno.valueZoneId());
            ParseUtil.append(body, "{}.{}={}.ofInstant({}.ofEpochMilli({}),{});\n", varNameInstance, field.getName(), ZonedDateTime.class.getName(), Instant.class.getName(), varNameLongField, varNameValueZoneId);
        } else if (long.class.isAssignableFrom(fieldTypeClass)) {
            ParseUtil.append(body, "{}.{}={};\n", varNameInstance, field.getName(), varNameLongField);
        } else {
            final String varNameValueZoneId = ParseUtil.defineClassVar(context, ZoneId.class, "{}.of(\"{}\")", ZoneId.class.getName(), anno.valueZoneId());
            final String dateTimeFormatterVarName = ParseUtil.defineClassVar(context, DateTimeFormatter.class, "{}.ofPattern(\"{}\").withZone({})", DateTimeFormatter.class.getName(), anno.stringFormat(), varNameValueZoneId);
            ParseUtil.append(body, "{}.{}={}.ofInstant({}.ofEpochMilli({}),{}).format({});\n",
                    varNameInstance,
                    field.getName(),
                    zoneDateTimeClassName,
                    Instant.class.getName(),
                    varNameLongField,
                    varNameValueZoneId,
                    dateTimeFormatterVarName);
        }
    }

    @Override
    public void buildDeParse(BuilderContext context) {
        final StringBuilder body = context.method_body;
        final F_date_bcd anno = context.field.getAnnotation(F_date_bcd.class);
        if (anno.skip()) {
            ParseUtil.appendSkip_deParse(6, "", context);
            return;
        }
        final Field field = context.field;
        final Class<?> fieldTypeClass = field.getType();
        final String valCode = varNameInstance + "." + field.getName();
        final String varNameField = ParseUtil.getFieldVarName(context);
        final String varNameZoneId = ParseUtil.defineZoneIdClassVar(context, anno.zoneId());
        final String varNameLongField = varNameField + "_long";
        final String zoneDateTimeClassName = ZonedDateTime.class.getName();
        //根据字段类型获取long
        if (Date.class.isAssignableFrom(fieldTypeClass)) {
            ParseUtil.append(body, "final long {}={}.getTime();\n", varNameLongField, valCode);
        } else if (Instant.class.isAssignableFrom(fieldTypeClass)) {
            ParseUtil.append(body, "final long {}={}.toEpochMilli();\n", varNameLongField, valCode);
        } else if (LocalDateTime.class.isAssignableFrom(fieldTypeClass)) {
            final String varNameValueZoneId = ParseUtil.defineClassVar(context, ZoneId.class, "{}.of(\"{}\")", ZoneId.class.getName(), anno.valueZoneId());
            ParseUtil.append(body, "final long {}={}.atZone({}).toInstant().toEpochMilli();\n", varNameLongField, valCode, varNameValueZoneId);
        } else if (OffsetDateTime.class.isAssignableFrom(fieldTypeClass)) {
            ParseUtil.append(body, "final long {}={}.toInstant().toEpochMilli();\n", varNameLongField, valCode);
        } else if (ZonedDateTime.class.isAssignableFrom(fieldTypeClass)) {
            ParseUtil.append(body, "final long {}={}.toInstant().toEpochMilli();\n", varNameLongField, valCode);
        } else if (long.class.isAssignableFrom(fieldTypeClass)) {
            ParseUtil.append(body, "final long {}={};\n", varNameLongField, valCode);
        } else {
            final String varNameValueZoneId = ParseUtil.defineClassVar(context, ZoneId.class, "{}.of(\"{}\")", ZoneId.class.getName(), anno.valueZoneId());
            final String dateTimeFormatterVarName = ParseUtil.defineClassVar(context, DateTimeFormatter.class, "{}.ofPattern(\"{}\").withZone({})", DateTimeFormatter.class.getName(), anno.stringFormat(), varNameValueZoneId);
            ParseUtil.append(body, "final long {}={}.parse({},{}).toInstant().toEpochMilli();\n", varNameLongField, zoneDateTimeClassName, valCode, dateTimeFormatterVarName);
        }
        ParseUtil.append(body, "{}.write({},{},{},{});\n", FieldBuilder__F_date_bcd.class.getName()
                , varNameByteBuf, varNameLongField, varNameZoneId, anno.baseYear());
    }

    public static long read(ByteBuf byteBuf, ZoneId zoneId, int baseYear) {
        int year = readBcd(byteBuf);
        int month = readBcd(byteBuf);
        int day = readBcd(byteBuf);
        int hour = readBcd(byteBuf);
        int minute = readBcd(byteBuf);
        int second = readBcd(byteBuf);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(baseYear + year, month, day, hour, minute, second, 0, zoneId);
        return zonedDateTime.toEpochSecond() * 1000;
    }

    public static long read(ByteBuf byteBuf, ZoneOffset zoneOffset, int baseYear) {
        int year = readBcd(byteBuf);
        int month = readBcd(byteBuf);
        int day = readBcd(byteBuf);
        int hour = readBcd(byteBuf);
        int minute = readBcd(byteBuf);
        int second = readBcd(byteBuf);
        return LocalDateTime.of(baseYear + year, month, day, hour, minute, second)
                .toEpochSecond(zoneOffset) * 1000;
    }

    private static int readBcd(ByteBuf byteBuf) {
        int value = byteBuf.readUnsignedByte();
        return (value >>> 4) * 10 + (value & 0x0f);
    }

    public static void write(ByteBuf byteBuf, long ts, ZoneId zoneId, int baseYear) {
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), zoneId);
        int year = zonedDateTime.getYear() - baseYear;
        int month = zonedDateTime.getMonthValue();
        int day = zonedDateTime.getDayOfMonth();
        int hour = zonedDateTime.getHour();
        int minute = zonedDateTime.getMinute();
        int second = zonedDateTime.getSecond();
        byte b1 = (byte) (((year / 10) << 4) | (year % 10));
        byte b2 = (byte) (((month / 10) << 4) | (month % 10));
        byte b3 = (byte) (((day / 10) << 4) | (day % 10));
        byte b4 = (byte) (((hour / 10) << 4) | (hour % 10));
        byte b5 = (byte) (((minute / 10) << 4) | (minute % 10));
        byte b6 = (byte) (((second / 10) << 4) | (second % 10));
        byteBuf.writeByte(b1)
                .writeByte(b2)
                .writeByte(b3)
                .writeByte(b4)
                .writeByte(b5)
                .writeByte(b6);
    }

    public static void write(ByteBuf byteBuf, long ts, ZoneOffset zoneOffset, int baseYear) {
        long seconds = Math.floorDiv(ts, 1000);
        int nanos = Math.floorMod(ts, 1000) * 1_000_000;
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(seconds, nanos, zoneOffset);
        writeBcd(byteBuf, dateTime.getYear() - baseYear);
        writeBcd(byteBuf, dateTime.getMonthValue());
        writeBcd(byteBuf, dateTime.getDayOfMonth());
        writeBcd(byteBuf, dateTime.getHour());
        writeBcd(byteBuf, dateTime.getMinute());
        writeBcd(byteBuf, dateTime.getSecond());
    }

    private static void writeBcd(ByteBuf byteBuf, int value) {
        byteBuf.writeByte((value / 10 << 4) | value % 10);
    }


}
