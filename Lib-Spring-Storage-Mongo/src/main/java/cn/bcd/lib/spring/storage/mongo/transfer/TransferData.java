package cn.bcd.lib.spring.storage.mongo.transfer;

import cn.bcd.lib.spring.storage.mongo.MongoData;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class TransferData implements MongoData {

    public String vin;          // vin码
    public int type;      // 报文类型 1=登录报文,2=实时报文,3=补发报文,4=登出报文
    public String platformCode; // 平台code
    public String loginSn;      // 车辆登录登出流水号
    public String hex;      // 报文

    public Date collectTime;  // 采集时间
    public Date gwInTime;
    public Date gwOutTime;
    public Date parseInTime;
    public Date parseOutTime;
    public Date transferInTime;
    public Date transferOutTime; // 转发时间


    @Override
    public String getPartitionId() {
        return vin;
    }

    @Override
    public String getId() {
        return MongoUtil_transferData.toId(vin, collectTime, platformCode, type);
    }
}
