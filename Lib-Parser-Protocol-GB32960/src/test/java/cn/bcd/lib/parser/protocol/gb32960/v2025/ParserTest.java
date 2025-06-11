package cn.bcd.lib.parser.protocol.gb32960.v2025;

import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.data.*;
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
        packet.encodeWay = new NumVal_byte(0, (byte) 1);

        VehicleRunData vehicleRunData = new VehicleRunData();
        vehicleRunData.collectTime = new Date();
        VehicleBaseData vehicleBaseData = new VehicleBaseData();
        vehicleBaseData.vehicleStatus = new NumVal_byte(0, (byte) 0x01);
        vehicleBaseData.chargeStatus = new NumVal_byte(0, (byte) 0x01);
        vehicleBaseData.runMode = new NumVal_byte(0, (byte) 0x01);
        vehicleBaseData.vehicleSpeed = new NumVal_float(0, 11.11F);
        vehicleBaseData.totalMileage = new NumVal_double(0, 67894.66);
        vehicleBaseData.totalVoltage = new NumVal_float(0, 22.32F);
        vehicleBaseData.totalCurrent = new NumVal_float(0, 0.01F);
        vehicleBaseData.soc = new NumVal_byte(0, (byte) 66);
        vehicleBaseData.dcStatus = new NumVal_byte(0, (byte) 66);
        vehicleBaseData.gearPosition = 1;
        vehicleBaseData.resistance = new NumVal_int(0, 8192);
        vehicleRunData.vehicleBaseData = vehicleBaseData;

        VehicleMotorData vehicleMotorData = new VehicleMotorData();
        vehicleMotorData.num = 1;
        MotorData motorData = new MotorData();
        motorData.no = 1;
        motorData.status = new NumVal_byte(0, (byte) 1);
        motorData.controllerTemperature = new NumVal_short(0, (short) 22);
        motorData.rotateSpeed = new NumVal_int(0, 1000);
        motorData.rotateRectangle = new NumVal_float(0, 0.1f);
        motorData.temperature = new NumVal_short(0, (short) 22);
        vehicleMotorData.content = new MotorData[]{motorData};
        vehicleRunData.vehicleMotorData = vehicleMotorData;

        VehicleFuelBatteryData vehicleFuelBatteryData = new VehicleFuelBatteryData();
        vehicleFuelBatteryData.maxTemperature = new NumVal_short(0, (short) 22);
        vehicleFuelBatteryData.maxTemperatureCode = new NumVal_short(0, (short) 1);
        vehicleFuelBatteryData.maxConcentration = new NumVal_float(0, 0.1f);
        vehicleFuelBatteryData.maxConcentrationCode = new NumVal_short(0, (short) 2);
        vehicleFuelBatteryData.maxPressure = new NumVal_float(0, 11.1f);
        vehicleFuelBatteryData.maxPressureCode = new NumVal_short(0, (short) 3);
        vehicleFuelBatteryData.dcStatus = new NumVal_byte(0, (byte) 4);
        vehicleFuelBatteryData.percent = new NumVal_byte(0, (byte) 55);
        vehicleFuelBatteryData.dcTemperature = new NumVal_short(0, (short) 66);
        vehicleRunData.vehicleFuelBatteryData = vehicleFuelBatteryData;

        VehicleEngineData vehicleEngineData = new VehicleEngineData();
        vehicleEngineData.speed = new NumVal_int(0, 132);
        vehicleRunData.vehicleEngineData = vehicleEngineData;

        VehiclePositionData vehiclePositionData = new VehiclePositionData();
        vehiclePositionData.status = 1;
        vehiclePositionData.type = 1;
        vehiclePositionData.lng = 113.945;
        vehiclePositionData.lat = 22.545;
        vehicleRunData.vehiclePositionData = vehiclePositionData;

        VehicleAlarmData vehicleAlarmData = new VehicleAlarmData();
        vehicleAlarmData.maxAlarmLevel = new NumVal_byte(0, (byte) 1);
        vehicleAlarmData.alarmFlag = 1;
        vehicleAlarmData.chargeBadNum = 2;
        vehicleAlarmData.chargeBadCodes = new long[]{100, 112};
        vehicleAlarmData.driverBadNum = 3;
        vehicleAlarmData.driverBadCodes = new long[]{111, 222, 333};
        vehicleAlarmData.engineBadNum = 2;
        vehicleAlarmData.engineBadCodes = new long[]{156, 166};
        vehicleAlarmData.otherBadNum = 2;
        vehicleAlarmData.otherBadCodes = new long[]{66, 77};
        vehicleAlarmData.commonBadNum = 1;
        vehicleAlarmData.commonBadCodes = new int[]{99};
        vehicleRunData.vehicleAlarmData = vehicleAlarmData;

        VehicleBatteryMinVoltageData vehicleBatteryMinVoltageData = new VehicleBatteryMinVoltageData();
        vehicleBatteryMinVoltageData.num = 1;
        BatteryMinVoltageData batteryMinVoltageData = new BatteryMinVoltageData();
        batteryMinVoltageData.no = new NumVal_byte(0, (byte) 1);
        batteryMinVoltageData.voltage = new NumVal_float(0, 12.3f);
        batteryMinVoltageData.current = new NumVal_float(0, 12.44f);
        batteryMinVoltageData.total = 5;
        batteryMinVoltageData.minVoltages = new NumVal_float[]{
                new NumVal_float(0, 12.3f),
                new NumVal_float(0, 12.4f),
                new NumVal_float(0, 12.5f),
                new NumVal_float(0, 12.6f),
                new NumVal_float(0, 12.7f)
        };
        vehicleBatteryMinVoltageData.datas = new BatteryMinVoltageData[]{batteryMinVoltageData};
        vehicleRunData.vehicleBatteryMinVoltageData = vehicleBatteryMinVoltageData;

        VehicleBatteryTemperatureData vehicleBatteryTemperatureData = new VehicleBatteryTemperatureData();
        vehicleBatteryTemperatureData.num = 1;
        BatteryTemperatureData batteryTemperatureData = new BatteryTemperatureData();
        batteryTemperatureData.no = new NumVal_byte(0, (byte) 1);
        batteryTemperatureData.num = 2;
        batteryTemperatureData.currents = new NumVal_short[]{
                new NumVal_short(1, (short) 12),
                new NumVal_short(0, (short) 13)
        };
        vehicleBatteryTemperatureData.datas = new BatteryTemperatureData[]{batteryTemperatureData};
        vehicleRunData.vehicleBatteryTemperatureData = vehicleBatteryTemperatureData;

        VehicleFuelBatteryHeapData vehicleFuelBatteryHeapData = new VehicleFuelBatteryHeapData();
        vehicleFuelBatteryHeapData.maxTemperature = 1;
        FuelBatteryHeapData fuelBatteryHeapData = new FuelBatteryHeapData();
        fuelBatteryHeapData.no = new NumVal_short(1, (short) 1);
        fuelBatteryHeapData.voltage = new NumVal_float(1, (float) 1.1);
        fuelBatteryHeapData.current = new NumVal_float(1, (float) 1.1);
        fuelBatteryHeapData.hydrogenPressure = new NumVal_float(1, (float) 1.1);
        fuelBatteryHeapData.airPressure = new NumVal_float(1, (float) 1.1);
        fuelBatteryHeapData.airTemperature = new NumVal_short(1, (short) 2);
        fuelBatteryHeapData.num = 3;
        fuelBatteryHeapData.temperatures = new NumVal_short[]{
                new NumVal_short(1, (short) 2),
                new NumVal_short(2, (short) 2),
                new NumVal_short(0, (short) 2)
        };
        vehicleFuelBatteryHeapData.datas = new FuelBatteryHeapData[]{fuelBatteryHeapData};
        vehicleRunData.vehicleFuelBatteryHeapData = vehicleFuelBatteryHeapData;

        VehicleSupercapacitorData vehicleSupercapacitorData = new VehicleSupercapacitorData();
        vehicleSupercapacitorData.no = new NumVal_short(1, (short) 2);
        vehicleSupercapacitorData.voltage = new NumVal_float(1, 1.1f);
        vehicleSupercapacitorData.current = new NumVal_float(1, 1.1f);
        vehicleSupercapacitorData.voltageNum = 1;
        vehicleSupercapacitorData.voltages = new NumVal_float[]{new NumVal_float(1, 1.1f)};
        vehicleSupercapacitorData.temperatureNum = 1;
        vehicleSupercapacitorData.temperatures = new NumVal_short[]{new NumVal_short(1, (short) 2)};
        vehicleRunData.vehicleSupercapacitorData = vehicleSupercapacitorData;

        VehicleSupercapacitorLimitValueData vehicleSupercapacitorLimitValueData = new VehicleSupercapacitorLimitValueData();
        vehicleSupercapacitorLimitValueData.maxVoltageSystemNo = new NumVal_short(1, (short) 2);
        vehicleSupercapacitorLimitValueData.maxVoltageCode = new NumVal_int(1, 2);
        vehicleSupercapacitorLimitValueData.maxVoltage = new NumVal_float(1, 2.0f);
        vehicleSupercapacitorLimitValueData.minVoltageSystemNo = new NumVal_short(1, (short) 2);
        vehicleSupercapacitorLimitValueData.minVoltageCode = new NumVal_int(1, 2);
        vehicleSupercapacitorLimitValueData.minVoltage = new NumVal_float(1, 2.0f);
        vehicleSupercapacitorLimitValueData.maxTemperatureSystemNo = new NumVal_short(1, (short) 2);
        vehicleSupercapacitorLimitValueData.maxTemperatureNo = new NumVal_int(1, 2);
        vehicleSupercapacitorLimitValueData.maxTemperature = new NumVal_short(1, (short) 33);
        vehicleSupercapacitorLimitValueData.minTemperatureSystemNo = new NumVal_short(1, (short) 2);
        vehicleSupercapacitorLimitValueData.minTemperatureNo = new NumVal_int(1, 2);
        vehicleSupercapacitorLimitValueData.minTemperature = new NumVal_short(1, (short) 44);
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
        PerformanceUtil.testPerformance(ByteBufUtil.decodeHexDump(data), threadNum, num, Packet::read, (buf, instance) -> instance.write(buf), true);
    }

}
