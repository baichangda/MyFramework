package cn.bcd.lib.parser.protocol.gb32960.v2025.processor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.data.DefaultNumValChecker;
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

    public final NumValGetter numValGetter = DefaultNumValChecker.instance;

    @Override
    public ParamData process(ByteBuf data, ProcessContext<?> processContext) {
        Object obj = processContext.instance;
        int num;
        if (obj instanceof ParamQueryResponse paramQueryResponse) {
            if (paramQueryResponse.num.type() == 0) {
                num = paramQueryResponse.num.val();
            } else {
                num = 0;
            }
        } else if (obj instanceof ParamSetRequest paramSetRequest) {
            if (paramSetRequest.num.type() == 0) {
                num = paramSetRequest.num.val();
            } else {
                num = 0;
            }
        } else {
            throw BaseException.get("instance[{}] not support", obj.getClass().getName());
        }
        ParamData paramData = new ParamData();
        for (int i = 0; i < num; i++) {
            byte paramId = data.readByte();
            DefaultNumValChecker.instance.getType(NumType.uint16, data.readUnsignedByte());
            switch (paramId) {
                case 0x01 ->
                        paramData.localStorageTimeCycle = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
                case 0x02 ->
                        paramData.normalReportTime = numValGetter.getNumVal_short(NumType.uint8, data.readUnsignedByte());
                case 0x03 ->
                        paramData.alarmReportTime = numValGetter.getNumVal_short(NumType.uint8, data.readUnsignedByte());
                case 0x04 -> paramData.remotePlatformNameLen = data.readUnsignedByte();
                case 0x05 ->
                        paramData.remotePlatformName = data.readCharSequence(paramData.remotePlatformNameLen, StandardCharsets.UTF_8).toString();
                case 0x06 ->
                        paramData.remotePlatformPort = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
                case 0x07 ->
                        paramData.terminalHardwareData = data.readCharSequence(5, StandardCharsets.UTF_8).toString();
                case 0x08 ->
                        paramData.terminalSoftwareData = data.readCharSequence(5, StandardCharsets.UTF_8).toString();
                case 0x09 ->
                        paramData.heartbeatSendCycleData = numValGetter.getNumVal_short(NumType.uint8, data.readUnsignedByte());
                case 0x0A ->
                        paramData.terminalResponseTimeoutData = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
                case 0x0B ->
                        paramData.domainResponseTimeoutData = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
                case 0x0C ->
                        paramData.loginFailureData = numValGetter.getNumVal_short(NumType.uint8, data.readUnsignedByte());
                case 0x0D -> paramData.publicPlatformNameLen = data.readUnsignedByte();
                case 0x0E ->
                        paramData.publicPlatformName = data.readCharSequence(paramData.publicPlatformNameLen, StandardCharsets.UTF_8).toString();
                case 0x0F ->
                        paramData.publicPlatformPort = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
                case 0x10 ->
                        paramData.samplingDetectionData = numValGetter.getNumVal_short(NumType.uint8, data.readUnsignedByte());
            }
        }
        return paramData;
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext<?> processContext, ParamData instance) {
        if (instance.localStorageTimeCycle != null) {
            data.writeByte(0x01);
            data.writeShort(numValGetter.getVal(NumType.uint16, instance.localStorageTimeCycle));
        }
        if (instance.normalReportTime != null) {
            data.writeByte(0x02);
            data.writeByte(numValGetter.getVal(NumType.uint8, instance.normalReportTime));
        }
        if (instance.alarmReportTime != null) {
            data.writeByte(0x03);
            data.writeShort(numValGetter.getVal(NumType.uint8, instance.alarmReportTime));
        }
        if (instance.remotePlatformName != null) {
            data.writeByte(0x04);
            data.writeByte(instance.remotePlatformName.length());
            data.writeByte(0x05);
            data.writeCharSequence(instance.remotePlatformName, StandardCharsets.UTF_8);
        }
        if (instance.remotePlatformPort != null) {
            data.writeByte(0x06);
            data.writeShort(numValGetter.getVal(NumType.uint16, instance.remotePlatformPort));
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
            data.writeShort(numValGetter.getVal(NumType.uint8, instance.remotePlatformPort));
        }
        if (instance.terminalResponseTimeoutData != null) {
            data.writeByte(0x0A);
            data.writeShort(numValGetter.getVal(NumType.uint16, instance.terminalResponseTimeoutData));
        }
        if (instance.domainResponseTimeoutData != null) {
            data.writeByte(0x0B);
            data.writeShort(numValGetter.getVal(NumType.uint16, instance.domainResponseTimeoutData));
        }
        if (instance.loginFailureData != null) {
            data.writeByte(0x0C);
            data.writeShort(numValGetter.getVal(NumType.uint8, instance.loginFailureData));
        }
        if (instance.publicPlatformName != null) {
            data.writeByte(0x0D);
            data.writeByte(instance.publicPlatformName.length());
            data.writeByte(0x0E);
            data.writeCharSequence(instance.publicPlatformName, StandardCharsets.UTF_8);
        }
        if (instance.publicPlatformPort != null) {
            data.writeByte(0x0F);
            data.writeShort(numValGetter.getVal(NumType.uint16, instance.publicPlatformPort));
        }
        if (instance.samplingDetectionData != null) {
            data.writeByte(0x10);
            data.writeShort(numValGetter.getVal(NumType.uint8, instance.samplingDetectionData));
        }
    }
}
