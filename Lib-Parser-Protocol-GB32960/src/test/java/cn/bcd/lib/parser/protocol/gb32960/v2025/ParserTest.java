package cn.bcd.lib.parser.protocol.gb32960.v2025;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.util.PerformanceUtil;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class ParserTest {
    static Logger logger = LoggerFactory.getLogger(ParserTest.class);

    @Test
    public void sample() {
        Parser.withDefaultLogCollector_parse();
        Parser.withDefaultLogCollector_deParse();
        Parser.enableGenerateClassFile();
        Parser.enablePrintBuildLog();
        Packet packet = new Packet();
        packet.header = new byte[]{0x24, 0x24};
        packet.flag = PacketFlag.vehicle_run_data;
        packet.replyFlag = 0xFE;
        packet.vin = "TEST0000000000001";
        packet.encodeWay = 1;

        VehicleRunData vehicleRunData = new VehicleRunData();
        vehicleRunData.collectTime = new Date();
        VehicleBaseData vehicleBaseData = new VehicleBaseData();
        vehicleBaseData.vehicleStatus = 1;
        vehicleBaseData.chargeStatus = 1;
        vehicleBaseData.runMode = 1;
        vehicleBaseData.vehicleSpeed = 11.11F;
        vehicleBaseData.totalMileage = 67894.66;
        vehicleBaseData.totalVoltage = 22.32F;
        vehicleBaseData.totalCurrent = 0.01F;
        vehicleBaseData.soc = 66;
        vehicleBaseData.dcStatus = 66;
        vehicleBaseData.gearPosition = 1;
        vehicleBaseData.resistance = 8192;
        vehicleRunData.vehicleBaseData = vehicleBaseData;

        VehicleMotorData vehicleMotorData = new VehicleMotorData();
        vehicleMotorData.num = 1;
        MotorData motorData = new MotorData();
        motorData.no = 1;
        motorData.status = 1;
        motorData.controllerTemperature = 22;
        motorData.rotateSpeed = 1000;
        motorData.rotateRectangle = 0.1f;
        motorData.temperature = 22;
        vehicleMotorData.content = new MotorData[]{motorData};
        vehicleRunData.vehicleMotorData = vehicleMotorData;

        VehicleFuelBatteryData vehicleFuelBatteryData = new VehicleFuelBatteryData();
        vehicleFuelBatteryData.maxTemperature = 22;
        vehicleFuelBatteryData.maxTemperatureCode = 1;
        vehicleFuelBatteryData.maxConcentration = 0.1F;
        vehicleFuelBatteryData.maxConcentrationCode = 2;
        vehicleFuelBatteryData.maxPressure = 11.1F;
        vehicleFuelBatteryData.maxPressureCode = 3;
        vehicleFuelBatteryData.dcStatus = 4;
        vehicleFuelBatteryData.percent = 55;
        vehicleFuelBatteryData.dcTemperature = 66;
        vehicleRunData.vehicleFuelBatteryData = vehicleFuelBatteryData;

        VehicleEngineData vehicleEngineData = new VehicleEngineData();
        vehicleEngineData.speed = 132;
        vehicleRunData.vehicleEngineData = vehicleEngineData;

        VehiclePositionData vehiclePositionData = new VehiclePositionData();
        vehiclePositionData.status = 1;
        vehiclePositionData.type = 1;
        vehiclePositionData.lng = 113.945;
        vehiclePositionData.lat = 22.545;
        vehicleRunData.vehiclePositionData = vehiclePositionData;

        VehicleAlarmData vehicleAlarmData = new VehicleAlarmData();
        vehicleAlarmData.maxAlarmLevel = 1;
        vehicleAlarmData.alarmFlag = 1;
        vehicleAlarmData.chargeBadNum = 2;
        vehicleAlarmData.chargeBadCodes = new long[]{100, 112};
        vehicleAlarmData.driverBadNum = 3;
        vehicleAlarmData.driverBadCodes = new long[]{111, 222, 333};
        vehicleAlarmData.engineBadNum__v = 1;
        vehicleAlarmData.otherBadNum = 2;
        vehicleAlarmData.otherBadCodes = new long[]{66, 77};
        vehicleAlarmData.commonBadNum = 1;
        vehicleAlarmData.commonBadCodes = new int[]{99};
        vehicleRunData.vehicleAlarmData = vehicleAlarmData;

        VehicleBatteryMinVoltageData vehicleBatteryMinVoltageData = new VehicleBatteryMinVoltageData();
        vehicleBatteryMinVoltageData.num = 1;
        BatteryMinVoltageData batteryMinVoltageData = new BatteryMinVoltageData();
        batteryMinVoltageData.no = 1;
        batteryMinVoltageData.voltage = 12.3F;
        batteryMinVoltageData.current = 12.44F;
        batteryMinVoltageData.total = 5;
        batteryMinVoltageData.minVoltages = new float[]{12.3f, 12.4f, 12.5f, 12.6f, 12.7f};
        batteryMinVoltageData.minVoltages__v = new byte[5];
        vehicleBatteryMinVoltageData.datas = new BatteryMinVoltageData[]{batteryMinVoltageData};
        vehicleRunData.vehicleBatteryMinVoltageData = vehicleBatteryMinVoltageData;

        VehicleBatteryTemperatureData vehicleBatteryTemperatureData = new VehicleBatteryTemperatureData();
        vehicleBatteryTemperatureData.num = 1;
        BatteryTemperatureData batteryTemperatureData = new BatteryTemperatureData();
        batteryTemperatureData.no = 1;
        batteryTemperatureData.num = 2;
        batteryTemperatureData.currents = new short[]{12, 13};
        batteryTemperatureData.currents__v = new byte[]{0, 1};
        vehicleBatteryTemperatureData.datas = new BatteryTemperatureData[]{batteryTemperatureData};
        vehicleRunData.vehicleBatteryTemperatureData = vehicleBatteryTemperatureData;

        VehicleFuelBatteryHeapData vehicleFuelBatteryHeapData = new VehicleFuelBatteryHeapData();
        vehicleFuelBatteryHeapData.num = 1;
        FuelBatteryHeapData fuelBatteryHeapData = new FuelBatteryHeapData();
        fuelBatteryHeapData.no = 1;
        fuelBatteryHeapData.voltage = 1.1F;
        fuelBatteryHeapData.current = 1.1F;
        fuelBatteryHeapData.hydrogenPressure = 1.1F;
        fuelBatteryHeapData.airPressure = 1.1F;
        fuelBatteryHeapData.airTemperature = 2;
        fuelBatteryHeapData.num = 0;
        vehicleFuelBatteryHeapData.datas = new FuelBatteryHeapData[]{fuelBatteryHeapData};
        vehicleRunData.vehicleFuelBatteryHeapData = vehicleFuelBatteryHeapData;

        VehicleSupercapacitorData vehicleSupercapacitorData = new VehicleSupercapacitorData();
        vehicleSupercapacitorData.no = 2;
        vehicleSupercapacitorData.voltage = 1.1F;
        vehicleSupercapacitorData.current = 1.1F;
        vehicleSupercapacitorData.current__v = 1;
        vehicleSupercapacitorData.voltageNum = 1;
        vehicleSupercapacitorData.voltages = new float[]{1.1F};
        vehicleSupercapacitorData.voltages__v = new byte[]{1};
        vehicleSupercapacitorData.temperatureNum = 1;
        vehicleSupercapacitorData.temperatures = new short[]{1};
        vehicleSupercapacitorData.temperatures__v = new byte[]{1};
        vehicleRunData.vehicleSupercapacitorData = vehicleSupercapacitorData;

        VehicleSupercapacitorLimitValueData vehicleSupercapacitorLimitValueData = new VehicleSupercapacitorLimitValueData();
        vehicleSupercapacitorLimitValueData.maxVoltageSystemNo = 2;
        vehicleSupercapacitorLimitValueData.maxVoltageCode = 2;
        vehicleSupercapacitorLimitValueData.maxVoltage = 2.0F;
        vehicleSupercapacitorLimitValueData.minVoltageSystemNo = 2;
        vehicleSupercapacitorLimitValueData.minVoltageCode = 2;
        vehicleSupercapacitorLimitValueData.minVoltage = 2.0F;
        vehicleSupercapacitorLimitValueData.maxTemperatureSystemNo = 2;
        vehicleSupercapacitorLimitValueData.maxTemperatureNo = 2;
        vehicleSupercapacitorLimitValueData.maxTemperature = 33;
        vehicleSupercapacitorLimitValueData.minTemperatureSystemNo = 2;
        vehicleSupercapacitorLimitValueData.minTemperatureNo = 2;
        vehicleSupercapacitorLimitValueData.minTemperature = 44;
        vehicleRunData.vehicleSupercapacitorLimitValueData = vehicleSupercapacitorLimitValueData;

        VehicleSignatureData vehicleSignatureData = new VehicleSignatureData();
        vehicleSignatureData.type = 1;
        vehicleSignatureData.rLen = 0;
        vehicleSignatureData.sLen = 0;
        vehicleRunData.vehicleSignatureData = vehicleSignatureData;

        packet.data = vehicleRunData;
        ByteBuf byteBuf1 = packet.toByteBuf_fixAll();
        String s1 = ByteBufUtil.hexDump(byteBuf1);
        Packet read = Packet.read(byteBuf1);
        ByteBuf byteBuf2 = read.toByteBuf();
        String s2 = ByteBufUtil.hexDump(byteBuf2);
        logger.info("\n{}\n{}", s1, s2);
        assert s1.equals(s2);
    }

    @Test
    public void test() {
        Parser.withDefaultLogCollector_parse();
        Parser.withDefaultLogCollector_deParse();
        Parser.enableGenerateClassFile();
        Parser.enablePrintBuildLog();
        String data = Const.sample_vehicleRunData;
        data = data.replaceAll(" ", "");
        byte[] bytes = ByteBufUtil.decodeHexDump(data);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        Packet packet = Packet.read(byteBuf);
        ByteBuf dest = Unpooled.buffer();
        packet.write(dest);
        logger.info(data.toUpperCase());
        logger.info(ByteBufUtil.hexDump(dest).toUpperCase());
        assert data.equalsIgnoreCase(ByteBufUtil.hexDump(dest));
    }

    @Test
    public void test_performance() {
        Parser.disableByteBufCheck();
        Parser.enablePrintBuildLog();
        Parser.enableGenerateClassFile();
        String data = Const.sample_vehicleRunData;
        int threadNum = 1;
        logger.info("param threadNum[{}]", threadNum);
        int num = Integer.MAX_VALUE;
        PerformanceUtil.testPerformance(ByteBufUtil.decodeHexDump(data), threadNum, num, Packet::read, (buf, instance) -> instance.write(buf), false);
    }

}
