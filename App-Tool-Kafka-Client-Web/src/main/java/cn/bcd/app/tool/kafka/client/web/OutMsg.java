package cn.bcd.app.tool.kafka.client.web;

import lombok.Data;

@Data
public class OutMsg {
    public boolean succeed;
    /**
     * 101、接收到kafka数据
     * 102、kafka断开通知
     */
    public int flag;
    public String data;
}
