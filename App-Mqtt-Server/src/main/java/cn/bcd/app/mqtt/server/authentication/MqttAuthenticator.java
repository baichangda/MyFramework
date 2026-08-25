package cn.bcd.app.mqtt.server.authentication;

/**
 * MQTT 客户端身份认证扩展点。
 */
@FunctionalInterface
public interface MqttAuthenticator {

    /**
     * 校验连接请求中的客户端凭据。
     *
     * @param request 认证请求
     * @return {@code true} 表示允许建立连接
     */
    boolean authenticate(MqttAuthenticationRequest request);
}
