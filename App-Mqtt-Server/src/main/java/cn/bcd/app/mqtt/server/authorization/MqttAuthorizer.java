package cn.bcd.app.mqtt.server.authorization;

/**
 * MQTT 发布、订阅操作的授权扩展点。
 */
@FunctionalInterface
public interface MqttAuthorizer {

    /**
     * 判断当前身份是否可以执行指定操作。
     *
     * @param request 授权上下文
     * @return {@code true} 表示允许操作
     */
    boolean authorize(MqttAuthorizationRequest request);
}
