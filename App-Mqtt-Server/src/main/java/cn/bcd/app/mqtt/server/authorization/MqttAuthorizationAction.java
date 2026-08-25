package cn.bcd.app.mqtt.server.authorization;

/**
 * 需要执行权限校验的 MQTT 操作类型。
 */
public enum MqttAuthorizationAction {
    /** 发布消息。 */
    PUBLISH,
    /** 订阅主题过滤器。 */
    SUBSCRIBE
}
