package cn.bcd.app.mqtt.server.authorization;

import java.util.Objects;

/**
 * 一次 MQTT 操作的授权上下文。
 *
 * @param action 待授权的操作
 * @param clientId 客户端标识
 * @param username 已认证的用户名，匿名连接时为 {@code null}
 * @param topic 发布时为主题名，订阅时为主题过滤器
 */
public record MqttAuthorizationRequest(
        MqttAuthorizationAction action,
        String clientId,
        String username,
        String topic
) {

    public MqttAuthorizationRequest {
        Objects.requireNonNull(action);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(topic);
    }
}
