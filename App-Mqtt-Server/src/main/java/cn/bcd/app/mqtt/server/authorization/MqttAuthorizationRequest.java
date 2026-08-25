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

    /**
     * 校验授权判断必需的操作、客户端标识和主题。
     *
     * @param action 操作类型
     * @param clientId 客户端标识
     * @param username 用户名
     * @param topic 主题名或主题过滤器
     */
    public MqttAuthorizationRequest {
        Objects.requireNonNull(action);
        Objects.requireNonNull(clientId);
        Objects.requireNonNull(topic);
    }
}
