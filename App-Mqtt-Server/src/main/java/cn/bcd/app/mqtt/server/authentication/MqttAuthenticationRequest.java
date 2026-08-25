package cn.bcd.app.mqtt.server.authentication;

import java.util.Objects;

/**
 * 客户端发起 CONNECT 时提交的身份凭据。
 *
 * <p>密码使用字节数组保存，并在构造和读取时复制，防止外部修改内部凭据。</p>
 *
 * @param clientId MQTT 客户端标识
 * @param username 用户名，协议中未携带时为 {@code null}
 * @param password 密码原始字节，协议中未携带时为 {@code null}
 */
public record MqttAuthenticationRequest(
        String clientId,
        String username,
        byte[] password
) {

    public MqttAuthenticationRequest {
        Objects.requireNonNull(clientId);
        password = password == null ? null : password.clone();
    }

    @Override
    public byte[] password() {
        return password == null ? null : password.clone();
    }
}
