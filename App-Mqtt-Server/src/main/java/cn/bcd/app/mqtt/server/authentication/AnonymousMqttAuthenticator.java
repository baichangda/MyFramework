package cn.bcd.app.mqtt.server.authentication;

/**
 * 匿名认证器，不校验用户名和密码，允许所有客户端连接。
 *
 * <p>仅适用于受信网络或开发环境；生产环境应改用具备身份校验能力的实现。</p>
 */
public final class AnonymousMqttAuthenticator implements MqttAuthenticator {

    @Override
    public boolean authenticate(MqttAuthenticationRequest request) {
        return true;
    }
}
