package cn.bcd.lib.storage.iotdb.gb32960;

import lombok.Data;

import java.time.Instant;
import java.util.Date;

@Data
public class RawData {
    public String vin;
    public Date collectTime;
    public int type;
    public Date gwReceiveTime;
    public Date gwSendTime;
    public Date parseReceiveTime;
    public String hex;

    public RawData() {
    }

    public RawData(String vin, Date collectTime, int type, Date gwReceiveTime, Date gwSendTime, Date parseReceiveTime, String hex) {
        this.vin = vin;
        this.collectTime = collectTime;
        this.type = type;
        this.gwReceiveTime = gwReceiveTime;
        this.gwSendTime = gwSendTime;
        this.parseReceiveTime = parseReceiveTime;
        this.hex = hex;
    }
}
