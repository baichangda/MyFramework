package cn.bcd.lib.parser.protocol.gb32960.v2025.processor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.data.DefaultNumValGetter;
import cn.bcd.lib.parser.base.data.NumType;
import cn.bcd.lib.parser.base.data.NumValGetter;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.ParamData;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.ParamQueryResponse;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.ParamSetRequest;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class ParamDataProcessor implements Processor<ParamData> {

    public final NumValGetter numValGetter = DefaultNumValGetter.instance;

    @Override
    public ParamData process(ByteBuf data, ProcessContext<?> processContext) {
        Object obj = processContext.instance;
        int num;
        if (obj instanceof ParamQueryResponse paramQueryResponse) {
            if (paramQueryResponse.num__v == 0) {
                num = paramQueryResponse.num;
            } else {
                num = 0;
            }
        } else if (obj instanceof ParamSetRequest paramSetRequest) {
            if (paramSetRequest.num__v == 0) {
                num = paramSetRequest.num;
            } else {
                num = 0;
            }
        } else {
            throw BaseException.get("instance[{}] not support", obj.getClass().getName());
        }
        ParamData paramData = new ParamData();
        for (int i = 0; i < num; i++) {
            byte paramId = data.readByte();
            DefaultNumValGetter.instance.getType(NumType.uint16, data.readUnsignedByte());
            switch (paramId) {
                case 0x01 -> {
                    int v = data.readUnsignedShort();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint16, v);
                    paramData.localStorageTimeCycle__v = type;
                    if (type == 0) {
                        paramData.localStorageTimeCycle = v;
                    }
                }
                case 0x02 -> {
                    short v = data.readUnsignedByte();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint8, v);
                    paramData.normalReportTime__v = type;
                    if (type == 0) {
                        paramData.normalReportTime = v;
                    }
                }
                case 0x03 -> {
                    short v = data.readUnsignedByte();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint8, v);
                    paramData.alarmReportTime__v = type;
                    if (type == 0) {
                        paramData.alarmReportTime = v;
                    }
                }
                case 0x04 -> paramData.remotePlatformNameLen = data.readUnsignedByte();
                case 0x05 ->
                        paramData.remotePlatformName = data.readCharSequence(paramData.remotePlatformNameLen, StandardCharsets.UTF_8).toString();
                case 0x06 -> {
                    int v = data.readUnsignedShort();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint16, v);
                    paramData.remotePlatformPort__v = type;
                    if (type == 0) {
                        paramData.remotePlatformPort = v;
                    }
                }
                case 0x07 ->
                        paramData.terminalHardwareData = data.readCharSequence(5, StandardCharsets.UTF_8).toString();
                case 0x08 ->
                        paramData.terminalSoftwareData = data.readCharSequence(5, StandardCharsets.UTF_8).toString();
                case 0x09 -> {
                    short v = data.readUnsignedByte();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint8, v);
                    paramData.heartbeatSendCycleData__v = type;
                    if (type == 0) {
                        paramData.heartbeatSendCycleData = v;
                    }
                }
                case 0x0A -> {
                    int v = data.readUnsignedShort();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint16, v);
                    paramData.terminalResponseTimeoutData__v = type;
                    if (type == 0) {
                        paramData.terminalResponseTimeoutData = v;
                    }
                }
                case 0x0B -> {
                    int v = data.readUnsignedShort();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint16, v);
                    paramData.domainResponseTimeoutData__v = type;
                    if (type == 0) {
                        paramData.domainResponseTimeoutData = v;
                    }
                }
                case 0x0C -> {
                    short v = data.readUnsignedByte();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint8, v);
                    paramData.loginFailureData__v = type;
                    if (type == 0) {
                        paramData.loginFailureData = v;
                    }
                }
                case 0x0D -> paramData.publicPlatformNameLen = data.readUnsignedByte();
                case 0x0E ->
                        paramData.publicPlatformName = data.readCharSequence(paramData.publicPlatformNameLen, StandardCharsets.UTF_8).toString();
                case 0x0F -> {
                    int v = data.readUnsignedShort();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint16, v);
                    paramData.publicPlatformPort__v = type;
                    if (type == 0) {
                        paramData.publicPlatformPort = v;
                    }
                }
                case 0x10 -> {
                    short v = data.readUnsignedByte();
                    byte type = DefaultNumValGetter.instance.getType(NumType.uint8, v);
                    paramData.samplingDetectionData__v = type;
                    if (type == 0) {
                        paramData.samplingDetectionData = v;
                    }
                }
            }
        }
        return paramData;
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext<?> processContext, ParamData instance) {
        if (instance.localStorageTimeCycle != null) {
            data.writeByte(0x01);
            if (instance.localStorageTimeCycle__v == 0) {
                data.writeShort(instance.localStorageTimeCycle);
            } else {
                data.writeShort(numValGetter.getVal_int(NumType.uint16, instance.localStorageTimeCycle__v));
            }
        }
        if (instance.normalReportTime != null) {
            data.writeByte(0x02);
            if (instance.normalReportTime__v == 0) {
                data.writeByte(instance.normalReportTime);
            } else {
                data.writeByte(numValGetter.getVal_int(NumType.uint8, instance.normalReportTime__v));
            }
        }
        if (instance.alarmReportTime != null) {
            data.writeByte(0x03);
            if (instance.alarmReportTime__v == 0) {
                data.writeByte(instance.alarmReportTime);
            } else {
                data.writeByte(numValGetter.getVal_int(NumType.uint8, instance.alarmReportTime__v));
            }
        }
        if (instance.remotePlatformName != null) {
            data.writeByte(0x04);
            data.writeByte(instance.remotePlatformName.length());
            data.writeByte(0x05);
            data.writeCharSequence(instance.remotePlatformName, StandardCharsets.UTF_8);
        }
        if (instance.remotePlatformPort != null) {
            data.writeByte(0x06);
            if (instance.remotePlatformPort__v == 0) {
                data.writeShort(instance.remotePlatformPort);
            } else {
                data.writeShort(numValGetter.getVal_int(NumType.uint16, instance.remotePlatformPort__v));
            }
        }
        if (instance.terminalHardwareData != null) {
            data.writeByte(0x07);
            data.writeCharSequence(instance.terminalHardwareData.substring(0, 5), StandardCharsets.UTF_8);
        }
        if (instance.terminalSoftwareData != null) {
            data.writeByte(0x08);
            data.writeCharSequence(instance.terminalSoftwareData.substring(0, 5), StandardCharsets.UTF_8);
        }
        if (instance.heartbeatSendCycleData != null) {
            data.writeByte(0x09);
            if (instance.heartbeatSendCycleData__v == 0) {
                data.writeByte(instance.heartbeatSendCycleData);
            } else {
                data.writeByte(numValGetter.getVal_int(NumType.uint8, instance.heartbeatSendCycleData__v));
            }
        }
        if (instance.terminalResponseTimeoutData != null) {
            data.writeByte(0x0A);
            if (instance.terminalResponseTimeoutData__v == 0) {
                data.writeShort(instance.terminalResponseTimeoutData);
            } else {
                data.writeShort(numValGetter.getVal_int(NumType.uint16, instance.terminalResponseTimeoutData__v));
            }
        }
        if (instance.domainResponseTimeoutData != null) {
            data.writeByte(0x0B);
            if (instance.domainResponseTimeoutData__v == 0) {
                data.writeShort(instance.domainResponseTimeoutData);
            } else {
                data.writeShort(numValGetter.getVal_int(NumType.uint16, instance.domainResponseTimeoutData__v));
            }
        }
        if (instance.loginFailureData != null) {
            data.writeByte(0x0C);
            if (instance.loginFailureData__v == 0) {
                data.writeByte(instance.loginFailureData);
            } else {
                data.writeByte(numValGetter.getVal_int(NumType.uint8, instance.loginFailureData__v));
            }
        }
        if (instance.publicPlatformName != null) {
            data.writeByte(0x0D);
            data.writeByte(instance.publicPlatformName.length());
            data.writeByte(0x0E);
            data.writeCharSequence(instance.publicPlatformName, StandardCharsets.UTF_8);
        }
        if (instance.publicPlatformPort != null) {
            data.writeByte(0x0F);
            if (instance.publicPlatformPort__v == 0) {
                data.writeShort(instance.publicPlatformPort);
            } else {
                data.writeShort(numValGetter.getVal_int(NumType.uint16, instance.publicPlatformPort__v));
            }
        }
        if (instance.samplingDetectionData != null) {
            data.writeByte(0x10);
            if (instance.samplingDetectionData__v == 0) {
                data.writeByte(instance.samplingDetectionData);
            } else {
                data.writeByte(numValGetter.getVal_int(NumType.uint8, instance.samplingDetectionData__v));
            }
        }
    }
}
