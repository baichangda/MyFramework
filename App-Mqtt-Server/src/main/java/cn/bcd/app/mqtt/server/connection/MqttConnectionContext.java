package cn.bcd.app.mqtt.server.connection;

import cn.bcd.app.mqtt.server.message.MqttWillMessage;

/**
 * CONNECT 成功后绑定到网络连接的只读上下文。
 *
 * @param clientId 客户端标识
 * @param cleanSession 是否要求清理旧会话
 * @param keepAliveSeconds 保活周期，0 表示禁用超时检查
 * @param username 已认证用户名
 * @param willMessage 遗嘱消息，未配置时为 {@code null}
 */
public record MqttConnectionContext(
        String clientId,
        boolean cleanSession,
        int keepAliveSeconds,
        String username,
        MqttWillMessage willMessage
) {
}
