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

    /**
     * 校验客户端标识，并复制密码字节以建立不可变请求。
     *
     * @param clientId 客户端标识
     * @param username 用户名
     * @param password 密码字节
     */
    public MqttAuthenticationRequest {
        Objects.requireNonNull(clientId);
        password = password == null ? null : password.clone();
    }

    /** 返回密码字节副本，避免调用方修改请求内部数据。 */
    @Override
    public byte[] password() {
        return password == null ? null : password.clone();
    }
}
