package cn.bcd.lib.parser.protocol.gb32960;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class ParamDataProcessorTest {
    private static final String[] VERSIONS = {"v2016", "v2025"};
    private static final String[] NUMERIC_FIELDS = {
            "localStorageTimeCycle", "normalReportTime", "alarmReportTime", "remotePlatformPort",
            "heartbeatSendCycleData", "terminalResponseTimeoutData", "domainResponseTimeoutData",
            "loginFailureData", "publicPlatformPort", "samplingDetectionData"
    };
    private static final int[] PARAM_IDS = {1, 2, 3, 6, 9, 10, 11, 12, 15, 16};

    @Test
    void numericParametersRoundTripIncludingZeroAndSpecialValues() throws Exception {
        for (String version : VERSIONS) {
            Processor<Object> processor = paramProcessor(version);
            for (int i = 0; i < NUMERIC_FIELDS.length; i++) {
                for (byte type = 0; type <= 2; type++) {
                    Object param = model(version, "ParamData");
                    String name = NUMERIC_FIELDS[i];
                    Field valueField = param.getClass().getField(name);
                    if (type == 0) {
                        if (valueField.getType() == Short.class) {
                            valueField.set(param, Short.valueOf((short) 0));
                        } else {
                            valueField.set(param, Integer.valueOf(0));
                        }
                    }
                    param.getClass().getField(name + "__v").setByte(param, type);
                    param.getClass().getField("num").setShort(param, (short) 99);
                    ByteBuf encoded = Unpooled.buffer();
                    ByteBuf reencoded = Unpooled.buffer();
                    try {
                        encoded.writeInt(123);
                        processor.deProcess(encoded, param);
                        assertEquals(1, param.getClass().getField("num").getShort(param));
                        assertEquals(1, encoded.getUnsignedByte(4));
                        assertEquals(PARAM_IDS[i], encoded.getUnsignedByte(5));
                        // Read literal wire sentinels to avoid merely checking symmetric encoding errors.
                        if (type != 0) {
                            int width = valueField.getType() == Short.class ? 1 : 2;
                            int expected = type == 1 ? 0xFF : 0xFE;
                            assertEquals(expected, encoded.getUnsignedByte(5 + width));
                            if (width == 2) {
                                assertEquals(0xFF, encoded.getUnsignedByte(6));
                            }
                        }
                        encoded.readerIndex(4);
                        Object parsed = processor.process(encoded);
                        assertFalse(encoded.isReadable());
                        assertEquals(type, parsed.getClass().getField(name + "__v").getByte(parsed));
                        if (type == 0) {
                            assertEquals(0, ((Number) valueField.get(parsed)).intValue());
                        } else {
                            assertNull(valueField.get(parsed));
                        }
                        processor.deProcess(reencoded, parsed);
                        assertEquals(encoded.getUnsignedByte(4), reencoded.getUnsignedByte(0));
                        assertEquals(encoded.slice(4, encoded.writerIndex() - 4), reencoded);
                    } finally {
                        encoded.release();
                        reencoded.release();
                    }
                }
            }
        }
    }

    @Test
    void countTracksAddedAndRemovedParametersIncludingDomainPairs() throws Exception {
        for (String version : VERSIONS) {
            Object param = model(version, "ParamData");
            param.getClass().getField("remotePlatformName").set(param, "a.example");
            param.getClass().getField("publicPlatformName").set(param, "b.example");
            param.getClass().getField("localStorageTimeCycle__v").setByte(param, (byte) 2);
            Processor<Object> processor = paramProcessor(version);
            ByteBuf encoded = Unpooled.buffer();
            try {
                processor.deProcess(encoded, param);
                assertEquals(5, encoded.getUnsignedByte(0));
                Object parsed = processor.process(encoded);
                assertFalse(encoded.isReadable());
                assertEquals(5, parsed.getClass().getField("num").getShort(parsed));
                assertEquals("a.example", parsed.getClass().getField("remotePlatformName").get(parsed));
                assertEquals("b.example", parsed.getClass().getField("publicPlatformName").get(parsed));
                param.getClass().getField("remotePlatformName").set(param, null);
                encoded.clear();
                processor.deProcess(encoded, param);
                assertEquals(3, param.getClass().getField("num").getShort(param));
                assertEquals(3, encoded.getUnsignedByte(0));
            } finally {
                encoded.release();
            }
        }
    }

    @Test
    void emptyParametersOverwriteStaleCount() throws Exception {
        for (String version : VERSIONS) {
            Object param = model(version, "ParamData");
            param.getClass().getField("num").setShort(param, (short) 10);
            ByteBuf encoded = Unpooled.buffer();
            try {
                paramProcessor(version).deProcess(encoded, param);
                assertEquals(0, param.getClass().getField("num").getShort(param));
                assertEquals(1, encoded.readableBytes());
                assertEquals(0, encoded.getUnsignedByte(0));
            } finally {
                encoded.release();
            }
        }
    }

    @Test
    void specialCountsRoundTripWithoutReadingFollowingBytes() throws Exception {
        for (String version : VERSIONS) {
            for (int raw : new int[]{0xFE, 0xFF}) {
                ByteBuf input = Unpooled.buffer().writeByte(raw).writeByte(0x55);
                ByteBuf output = Unpooled.buffer();
                try {
                    Processor<Object> processor = paramProcessor(version);
                    Object param = processor.process(input);
                    assertEquals(1, input.readerIndex());
                    assertEquals(raw == 0xFF ? 1 : 2, param.getClass().getField("num__v").getByte(param));
                    processor.deProcess(output, param);
                    assertEquals(1, output.readableBytes());
                    assertEquals(raw, output.readUnsignedByte());
                } finally {
                    input.release();
                    output.release();
                }
            }
        }
    }

    @Test
    void queryResponseAndSetRequestKeepWireLayout() throws Exception {
        for (String version : VERSIONS) {
            for (String name : new String[]{"ParamQueryResponse", "ParamSetRequest"}) {
                Object packetData = model(version, name);
                packetData.getClass().getField(name.equals("ParamQueryResponse") ? "queryTime" : "setTime")
                        .set(packetData, new Date(1767225600000L));
                Object param = model(version, "ParamData");
                param.getClass().getField("localStorageTimeCycle").set(param, 123);
                packetData.getClass().getField("paramData").set(packetData, param);
                Processor<Object> processor = modelProcessor(packetData.getClass());
                ByteBuf encoded = Unpooled.buffer();
                try {
                    processor.deProcess(encoded, packetData);
                    assertEquals(10, encoded.readableBytes());
                    assertEquals(1, encoded.getUnsignedByte(6));
                    assertEquals(1, encoded.getUnsignedByte(7));
                    assertEquals(123, encoded.getUnsignedShort(8));
                    Object parsed = processor.process(encoded);
                    assertFalse(encoded.isReadable());
                    Object parsedParam = parsed.getClass().getField("paramData").get(parsed);
                    assertEquals(1, parsedParam.getClass().getField("num").getShort(parsedParam));
                    assertEquals(123, parsedParam.getClass().getField("localStorageTimeCycle").get(parsedParam));
                } finally {
                    encoded.release();
                }
            }
        }
    }

    private static Object model(String version, String name) throws Exception {
        return Class.forName("cn.bcd.lib.parser.protocol.gb32960." + version + ".data." + name)
                .getConstructor().newInstance();
    }

    @SuppressWarnings("unchecked")
    private static Processor<Object> paramProcessor(String version) throws Exception {
        return (Processor<Object>) Class.forName("cn.bcd.lib.parser.protocol.gb32960." + version
                + ".processor.ParamDataProcessor").getConstructor().newInstance();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Processor<Object> modelProcessor(Class<?> type) {
        return Parser.getProcessor((Class) type);
    }
}
