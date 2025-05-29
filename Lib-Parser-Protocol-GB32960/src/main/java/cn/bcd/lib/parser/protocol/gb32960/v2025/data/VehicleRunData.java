package cn.bcd.lib.parser.protocol.gb32960.v2025.data;



import java.util.Date;
import java.util.List;

public class VehicleRunData implements PacketData {
    //数据采集时间
    public Date collectTime;
    //整车数据
    public VehicleBaseData vehicleBaseData;
    //驱动电机数据
    public VehicleMotorData vehicleMotorData;
    //燃料电池数据
    public VehicleFuelBatteryData vehicleFuelBatteryData;
    //发动机数据
    public VehicleEngineData vehicleEngineData;
    //车辆位置数据
    public VehiclePositionData vehiclePositionData;
    //报警数据
    public VehicleAlarmData vehicleAlarmData;
    //动力蓄电池最小并联单元电压数据
    public VehicleBatteryMinVoltageData vehicleBatteryMinVoltageData;
    //动力蓄电池温度数据
    public VehicleBatteryTemperatureData vehicleBatteryTemperatureData;
    //燃料电池电堆数据
    public VehicleFuelBatteryHeapData vehicleFuelBatteryHeapData;
    //燃料电池电堆数据
    public VehicleSupercapacitorData vehicleSupercapacitorData;
    //燃料电池电堆数据
    public VehicleSupercapacitorLimitValueData vehicleSupercapacitorLimitValueData;
    //车辆自定义数据
    public List<VehicleCustomData> vehicleCustomDatas;
    //车端信息签名
    public VehicleSignatureData vehicleSignatureData;
}
