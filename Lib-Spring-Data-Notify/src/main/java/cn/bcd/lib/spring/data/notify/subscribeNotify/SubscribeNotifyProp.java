package cn.bcd.lib.spring.data.notify.subscribeNotify;

import lombok.Data;

@Data
public class SubscribeNotifyProp {
    public boolean enableServer;
    public boolean enableClient;

    /**
     * 必须全局唯一
     * 即时同一类型服务、也必须不一样
     */
    public String groupId;
}
