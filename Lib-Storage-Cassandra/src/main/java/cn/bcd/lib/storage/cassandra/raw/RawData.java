package cn.bcd.lib.storage.cassandra.raw;

import lombok.Data;

import java.time.Instant;

@Data
public class RawData{
    public String vin;
    public Instant collectTime;
    public int type;
    public Instant gwReceiveTime;
    public Instant gwSendTime;
    public Instant parseReceiveTime;
    public String hex;

    public RawData() {
    }

    public RawData(String vin, Instant collectTime, int type, Instant gwReceiveTime, Instant gwSendTime, Instant parseReceiveTime, String hex) {
        this.vin = vin;
        this.collectTime = collectTime;
        this.type = type;
        this.gwReceiveTime = gwReceiveTime;
        this.gwSendTime = gwSendTime;
        this.parseReceiveTime = parseReceiveTime;
        this.hex = hex;
    }
}
