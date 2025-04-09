package cn.bcd.lib.parser.protocol.gb32960.processor;

import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.base.anno.data.DefaultNumValChecker;
import cn.bcd.lib.parser.base.anno.data.NumType;
import cn.bcd.lib.parser.base.anno.data.NumValGetter;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_date_bytes_6;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.data.ParamData;
import cn.bcd.lib.parser.protocol.gb32960.data.ParamQueryResponse;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public class ParamQueryResponseProcessor implements Processor<ParamQueryResponse> {

    public final NumValGetter numValGetter = DefaultNumValChecker.instance;

    @Override
    public ParamQueryResponse process(ByteBuf data, ProcessContext<?> processContext) {
        ParamQueryResponse instance = new ParamQueryResponse();
        instance.queryTime = new Date(FieldBuilder__F_date_bytes_6.read(data, DateZoneUtil.ZONE_OFFSET, 2000));
        instance.num = data.readUnsignedByte();
        ParamData paramData = new ParamData();
        instance.paramData = paramData;
        for (int i = 0; i < instance.num; i++) {
            byte paramId = data.readByte();
            DefaultNumValChecker.instance.getType(NumType.uint16, data.readUnsignedByte());
            switch (paramId) {
                case 0x01 ->
                        paramData.localStorageTimeCycle = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
                case 0x02 ->
                        paramData.normalReportTime = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
                case 0x03 ->
                        paramData.alarmReportTime = numValGetter.getNumVal_int(NumType.uint16, data.readUnsignedShort());
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
        return instance;
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext<?> processContext, ParamQueryResponse instance) {
        FieldBuilder__F_date_bytes_6.write(data, instance.queryTime.getTime(), DateZoneUtil.ZONE_OFFSET, 2000);
        data.writeByte(instance.num);
        ParamData paramData = instance.paramData;
        if (paramData.localStorageTimeCycle != null) {
            data.writeByte(0x01);
            data.writeShort(numValGetter.getVal(NumType.uint16, paramData.localStorageTimeCycle));
        }
        if (paramData.normalReportTime != null) {
            data.writeByte(0x02);
            data.writeShort(numValGetter.getVal(NumType.uint16, paramData.normalReportTime));
        }
        if (paramData.alarmReportTime != null) {
            data.writeByte(0x03);
            data.writeShort(numValGetter.getVal(NumType.uint16, paramData.alarmReportTime));
        }
        if (paramData.remotePlatformName != null) {
            data.writeByte(0x04);
            data.writeByte(paramData.remotePlatformName.length());
            data.writeByte(0x05);
            data.writeCharSequence(paramData.remotePlatformName,StandardCharsets.UTF_8);
        }
        if (paramData.remotePlatformPort != null) {
            data.writeByte(0x06);
            data.writeShort(numValGetter.getVal(NumType.uint16, paramData.remotePlatformPort));
        }
        if (paramData.terminalHardwareData != null) {
            data.writeByte(0x07);
            data.writeCharSequence(paramData.terminalHardwareData.substring(0, 5), StandardCharsets.UTF_8);
        }
        if (paramData.terminalSoftwareData != null) {
            data.writeByte(0x08);
            data.writeCharSequence(paramData.terminalSoftwareData.substring(0, 5), StandardCharsets.UTF_8);
        }
        if (paramData.heartbeatSendCycleData != null) {
            data.writeByte(0x09);
            data.writeShort(numValGetter.getVal(NumType.uint8, paramData.remotePlatformPort));
        }
        if (paramData.terminalResponseTimeoutData != null) {
            data.writeByte(0x0A);
            data.writeShort(numValGetter.getVal(NumType.uint16, paramData.terminalResponseTimeoutData));
        }
        if (paramData.domainResponseTimeoutData != null) {
            data.writeByte(0x0B);
            data.writeShort(numValGetter.getVal(NumType.uint16, paramData.domainResponseTimeoutData));
        }
        if (paramData.loginFailureData != null) {
            data.writeByte(0x0C);
            data.writeShort(numValGetter.getVal(NumType.uint8, paramData.loginFailureData));
        }
        if (paramData.publicPlatformName != null) {
            data.writeByte(0x0D);
            data.writeByte(paramData.publicPlatformName.length());
            data.writeByte(0x0E);
            data.writeCharSequence(paramData.publicPlatformName, StandardCharsets.UTF_8);
        }
        if (paramData.publicPlatformPort != null) {
            data.writeByte(0x0F);
            data.writeShort(numValGetter.getVal(NumType.uint16, paramData.publicPlatformPort));
        }
        if (paramData.samplingDetectionData != null) {
            data.writeByte(0x10);
            data.writeShort(numValGetter.getVal(NumType.uint8, paramData.samplingDetectionData));
        }
    }
}
