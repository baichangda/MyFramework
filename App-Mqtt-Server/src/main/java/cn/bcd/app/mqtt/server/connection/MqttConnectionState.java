package cn.bcd.app.mqtt.server.connection;

/** MQTT 网络连接的生命周期状态。 */
public enum MqttConnectionState {
    /** 通道已建立，尚未完成 CONNECT。 */
    NEW,
    /** 已通过认证并完成会话注册。 */
    CONNECTED,
    /** 已发起通道关闭。 */
    CLOSING,
    /** 通道已经失效。 */
    CLOSED
}
