package cn.bcd.app.mqtt.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mqtt.server")
public class MqttServerProperties {

    private boolean enabled = true;
    private String bindAddress = "0.0.0.0";
    private int port = 1883;
    private int maxPacketSize = 1024 * 1024;
    private int maxClientIdLength = 65535;
    private Limits limits = new Limits();

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

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public static class Limits {
        private int clientIds = 100000;
        private int subscriptionsPerSession = 1024;
        private int inflightMessagesPerSession = 1024;
        private int offlineMessagesPerSession = 10000;
        private long offlineQueueBytesPerSession = 64L * 1024 * 1024;
        private int retainedMessages = 100000;
        private long retainedMessageBytes = 256L * 1024 * 1024;

        public int getClientIds() { return clientIds; }
        public void setClientIds(int value) { clientIds = value; }
        public int getSubscriptionsPerSession() { return subscriptionsPerSession; }
        public void setSubscriptionsPerSession(int value) { subscriptionsPerSession = value; }
        public int getInflightMessagesPerSession() { return inflightMessagesPerSession; }
        public void setInflightMessagesPerSession(int value) { inflightMessagesPerSession = value; }
        public int getOfflineMessagesPerSession() { return offlineMessagesPerSession; }
        public void setOfflineMessagesPerSession(int value) { offlineMessagesPerSession = value; }
        public long getOfflineQueueBytesPerSession() { return offlineQueueBytesPerSession; }
        public void setOfflineQueueBytesPerSession(long value) { offlineQueueBytesPerSession = value; }
        public int getRetainedMessages() { return retainedMessages; }
        public void setRetainedMessages(int value) { retainedMessages = value; }
        public long getRetainedMessageBytes() { return retainedMessageBytes; }
        public void setRetainedMessageBytes(long value) { retainedMessageBytes = value; }
    }
}
