package cn.bcd.app.mqtt.server.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "mqtt.server.enabled=false",
        "mqtt.server.authentication.type=simple",
        "mqtt.server.authentication.simple.users.device=secret",
        "mqtt.server.persistence.session.sqlite.database-path=:memory:",
        "mqtt.server.persistence.retained-message.sqlite.database-path=:memory:"
})
class SimpleMqttAuthenticationContextTest {

    @Autowired
    MqttAuthenticator authenticator;

    @Test
    void shouldSelectSimpleAuthenticatorAndBindUsers() {
        assertInstanceOf(SimpleMqttAuthenticator.class, authenticator);
        assertTrue(authenticator.authenticate(new MqttAuthenticationRequest(
                "cid",
                "device",
                "secret".getBytes(StandardCharsets.UTF_8))));
    }
}
