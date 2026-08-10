package cn.bcd.lib.spring.redis.register;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "register")
public class RegisterProp {
    /**
     * 当前服务实例地址，例如 ip:端口；为空时仅启用服务发现。
     */
    public String host = "";

    /**
     * 当前实例提供的服务类型。
     */
    public RegisterServer[] servers = new RegisterServer[0];

    public RegisterServer[] getServers() {
        return servers == null ? new RegisterServer[0] : servers.clone();
    }

    public void setServers(RegisterServer[] servers) {
        this.servers = servers == null ? new RegisterServer[0] : servers.clone();
    }

    public String getHost() {
        return host == null ? "" : host;
    }

    public void setHost(String host) {
        this.host = host == null ? "" : host.trim();
    }
}
