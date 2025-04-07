package cn.bcd.lib.storage.mongo.gb32960;

import cn.bcd.lib.storage.mongo.MongoData;
import lombok.Data;

import java.util.Date;

@Data
public class RawData implements MongoData {
    public String vin;
    public Date collectTime;
    public int type;
    public Date gwReceiveTime;
    public Date gwSendTime;
    public Date parseReceiveTime;
    public String hex;

    @Override
    public String getPartitionId() {
        return vin;
    }

    @Override
    public String getId() {
        return MongoUtil_gb32960.toId(vin, collectTime, type);
    }
}
