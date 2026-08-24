package cn.bcd.app.mqtt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt.server")
public class MqttServerProperties {

    private boolean enabled = true;
    private String bindAddress = "0.0.0.0";
    private int port = 1883;
    private int maxPacketSize = 1024 * 1024;
    private int maxClientIdLength = 65535;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public int getMaxClientIdLength() {
        return maxClientIdLength;
    }

    public void setMaxClientIdLength(int maxClientIdLength) {
        this.maxClientIdLength = maxClientIdLength;
    }
}
