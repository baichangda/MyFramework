package cn.bcd.dataProcess.gateway;

public interface RedisKeyConst {


    /**
     * set key val timeout
     * key: 网关在线状态key前缀、后面接网关id
     */
    String gatewayOnline_redisKeyPre = "gw-online-";
}
