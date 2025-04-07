package cn.bcd.lib.data.notify.onlyNotify.vehicleData;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class VehicleData {
    // 车辆id
    private Long id;
    // 车架号
    private String vin;
    // 品牌编码
    private String brandCode;
    // T-BOX SN号
    private String tBoxSn;
    // 车型编码
    private String vehicleModelCode;
    // sim卡号
    private String simCardNumber;
    //SIM卡状态 0.暂无,1.已激活,2.已停用,3.可激活
    public Integer simStatus;
    // iccid
    private String iccid;
}