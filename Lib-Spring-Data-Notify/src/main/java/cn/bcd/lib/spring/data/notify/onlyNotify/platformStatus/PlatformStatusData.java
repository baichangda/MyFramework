package cn.bcd.lib.spring.data.notify.onlyNotify.platformStatus;

import lombok.Data;

import java.util.Date;

@Data
public class PlatformStatusData {

    /**
     * 0、下线
     * 1、上线
     */
    public int status;
    public String serverId;
    public Date time;
}
