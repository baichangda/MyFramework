package cn.bcd.lib.parser.protocol.gb32960.v2025.processor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.builder.FieldBuilder__F_date_bytes_6;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.*;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;

public class VehicleRunDataProcessor implements Processor<VehicleRunData> {

    Logger logger = LoggerFactory.getLogger(VehicleRunDataProcessor.class);

    final Processor<VehicleBaseData> processor_vehicleBaseData = Parser.getProcessor(VehicleBaseData.class);
    final Processor<VehicleMotorData> processor_vehicleMotorData = Parser.getProcessor(VehicleMotorData.class);
    final Processor<VehicleFuelBatteryData> processor_vehicleFuelBatteryData = Parser.getProcessor(VehicleFuelBatteryData.class);
    final Processor<VehicleEngineData> processor_vehicleEngineData = Parser.getProcessor(VehicleEngineData.class);
    final Processor<VehiclePositionData> processor_vehiclePositionData = Parser.getProcessor(VehiclePositionData.class);
    final Processor<VehicleAlarmData> processor_vehicleAlarmData = Parser.getProcessor(VehicleAlarmData.class);
    final Processor<VehicleSignatureData> processor_vehicleSignatureData = Parser.getProcessor(VehicleSignatureData.class);
    final Processor<VehicleBatteryMinVoltageData> processor_vehicleBatteryMinVoltageData = Parser.getProcessor(VehicleBatteryMinVoltageData.class);
    final Processor<VehicleBatteryTemperatureData> processor_vehicleBatteryTemperatureData = Parser.getProcessor(VehicleBatteryTemperatureData.class);
    final Processor<VehicleFuelBatteryHeapData> processor_vehicleFuelBatteryHeapData = Parser.getProcessor(VehicleFuelBatteryHeapData.class);
    final Processor<VehicleSupercapacitorData> processor_vehicleSupercapacitorData = Parser.getProcessor(VehicleSupercapacitorData.class);
    final Processor<VehicleSupercapacitorLimitValueData> processor_vehicleSupercapacitorLimitValueData = Parser.getProcessor(VehicleSupercapacitorLimitValueData.class);

    @Override
    public VehicleRunData process(ByteBuf data, ProcessContext<?> processContext) {
        VehicleRunData instance = new VehicleRunData();
        ProcessContext<?> parentContext = new ProcessContext<>(instance, processContext);
        instance.collectTime = new Date(FieldBuilder__F_date_bytes_6.read(data, DateZoneUtil.ZONE_OFFSET, 2000));
        final Packet packet = (Packet) processContext.instance;
        int allLen = packet.contentLength - 6;
        int beginLeave = data.readableBytes();
        while ((beginLeave - data.readableBytes()) < allLen) {
            short flag = data.readUnsignedByte();
            switch (flag) {
                case 1 -> {
                    //整车数据
                    instance.vehicleBaseData = processor_vehicleBaseData.process(data, parentContext);
                }
                case 2 -> {
                    //驱动电机数据
                    instance.vehicleMotorData = processor_vehicleMotorData.process(data, parentContext);
                }
                case 3 -> {
                    //燃料电池发动机及车载氢系统数据
                    instance.vehicleFuelBatteryData = processor_vehicleFuelBatteryData.process(data, parentContext);
                }
                case 4 -> {
                    //发动机数据
                    instance.vehicleEngineData = processor_vehicleEngineData.process(data, parentContext);
                }
                case 5 -> {
                    //车辆位置数据
                    instance.vehiclePositionData = processor_vehiclePositionData.process(data, parentContext);
                }
                case 6 -> {
                    //报警数据
                    instance.vehicleAlarmData = processor_vehicleAlarmData.process(data, parentContext);
                }
                case 7 -> {
                    //动力蓄电池最小并联单元电压数据\
                    instance.vehicleBatteryMinVoltageData = processor_vehicleBatteryMinVoltageData.process(data, parentContext);
                }
                case 8 -> {
                    //动力蓄电池温度数据
                    instance.vehicleBatteryTemperatureData = processor_vehicleBatteryTemperatureData.process(data, parentContext);
                }
                case 0x30 -> {
                    //燃料电池电堆数据
                    instance.vehicleFuelBatteryHeapData = processor_vehicleFuelBatteryHeapData.process(data, parentContext);
                }
                case 0x31 -> {
                    //超级电容器数据
                    instance.vehicleSupercapacitorData = processor_vehicleSupercapacitorData.process(data, parentContext);
                }
                case 0x32 -> {
                    //超级电容器极值数据
                    instance.vehicleSupercapacitorLimitValueData = processor_vehicleSupercapacitorLimitValueData.process(data, parentContext);
                }
                case 0xFF -> {
                    //车端数字签名
                    instance.vehicleSignatureData = processor_vehicleSignatureData.process(data, parentContext);
                }
                default -> {
                    if (flag >= 0x80 && flag <= 0xFE) {
                        //自定义数据
                        if (instance.vehicleCustomDatas == null) {
                            instance.vehicleCustomDatas = new ArrayList<>();
                        }
                        instance.vehicleCustomDatas.add(VehicleCustomData.read(flag, data));
                    } else {
                        throw BaseException.get("flag[{}] not support", flag);
                    }
                }
            }
        }
        return instance;
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext<?> processContext, VehicleRunData instance) {
        ProcessContext<?> parentContext = new ProcessContext<>(instance, processContext);
        FieldBuilder__F_date_bytes_6.write(data, instance.collectTime.getTime(), DateZoneUtil.ZONE_OFFSET, 2000);
        if (instance.vehicleBaseData != null) {
            data.writeByte(1);
            processor_vehicleBaseData.deProcess(data, parentContext, instance.vehicleBaseData);
        }
        if (instance.vehicleMotorData != null) {
            data.writeByte(2);
            processor_vehicleMotorData.deProcess(data, parentContext, instance.vehicleMotorData);
        }
        if (instance.vehicleFuelBatteryData != null) {
            data.writeByte(3);
            processor_vehicleFuelBatteryData.deProcess(data, parentContext, instance.vehicleFuelBatteryData);
        }
        if (instance.vehicleEngineData != null) {
            data.writeByte(4);
            processor_vehicleEngineData.deProcess(data, parentContext, instance.vehicleEngineData);
        }
        if (instance.vehiclePositionData != null) {
            data.writeByte(5);
            processor_vehiclePositionData.deProcess(data, parentContext, instance.vehiclePositionData);
        }
        if (instance.vehicleAlarmData != null) {
            data.writeByte(6);
            processor_vehicleAlarmData.deProcess(data, parentContext, instance.vehicleAlarmData);
        }
        if (instance.vehicleBatteryMinVoltageData != null) {
            data.writeByte(7);
            processor_vehicleBatteryMinVoltageData.deProcess(data, parentContext, instance.vehicleBatteryMinVoltageData);
        }
        if (instance.vehicleBatteryTemperatureData != null) {
            data.writeByte(8);
            processor_vehicleBatteryTemperatureData.deProcess(data, parentContext, instance.vehicleBatteryTemperatureData);
        }
        if (instance.vehicleFuelBatteryHeapData != null) {
            data.writeByte(0x30);
            processor_vehicleFuelBatteryHeapData.deProcess(data, parentContext, instance.vehicleFuelBatteryHeapData);
        }
        if (instance.vehicleSupercapacitorData != null) {
            data.writeByte(0x31);
            processor_vehicleSupercapacitorData.deProcess(data, parentContext, instance.vehicleSupercapacitorData);
        }
        if (instance.vehicleSupercapacitorLimitValueData != null) {
            data.writeByte(0x32);
            processor_vehicleSupercapacitorLimitValueData.deProcess(data, parentContext, instance.vehicleSupercapacitorLimitValueData);
        }
        if (instance.vehicleCustomDatas != null && !instance.vehicleCustomDatas.isEmpty()) {
            for (VehicleCustomData vehicleCustomData : instance.vehicleCustomDatas) {
                vehicleCustomData.write(data, vehicleCustomData);
            }
        }
        if (instance.vehicleSignatureData != null) {
            data.writeByte(0xFF);
            processor_vehicleSignatureData.deProcess(data, parentContext, instance.vehicleSignatureData);
        }
    }
}
