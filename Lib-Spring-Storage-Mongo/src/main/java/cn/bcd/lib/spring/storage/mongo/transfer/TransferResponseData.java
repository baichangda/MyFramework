package cn.bcd.lib.spring.storage.mongo.transfer;

import cn.bcd.lib.spring.storage.mongo.MongoData;
import lombok.Data;

import java.util.Date;

@Data
public class TransferResponseData implements MongoData {
    private String vin;
    private Date collectTime;
    private String platformCode;
    private int type;
    private int replyFlag;       // 结果 1=成功,2=失败
    private String hex;  // 返回报文

    @Override
    public String getPartitionId() {
        return vin;
    }

    @Override
    public String getId() {
        return MongoUtil_transferData.toId(vin, collectTime, platformCode, type);
    }
}
