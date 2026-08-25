package cn.bcd.app.mqtt.server.authentication;

import cn.bcd.app.mqtt.server.config.MqttAuthenticationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 基于配置文件中用户名、明文密码映射的简单认证器。
 *
 * <p>密码使用恒定时间比较，减少根据比较耗时推断密码内容的风险。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.authentication",
        name = "type",
        havingValue = "simple")
public final class SimpleMqttAuthenticator implements MqttAuthenticator {

    private final Map<String, String> users;

    /**
     * 从配置中复制用户表，避免运行期间受配置对象修改影响。
     *
     * @param properties 认证配置
     */
    public SimpleMqttAuthenticator(MqttAuthenticationProperties properties) {
        users = Map.copyOf(properties.getSimple().getUsers());
    }

    /**
     * 校验用户名是否存在，并以恒定时间比较密码字节。
     *
     * @param request 认证请求
     */
    @Override
    public boolean authenticate(MqttAuthenticationRequest request) {
        byte[] password = request.password();
        if (request.username() == null || password == null) {
            return false;
        }
        String expectedPassword = users.get(request.username());
        return expectedPassword != null && MessageDigest.isEqual(
                expectedPassword.getBytes(StandardCharsets.UTF_8),
                password);
    }
}
