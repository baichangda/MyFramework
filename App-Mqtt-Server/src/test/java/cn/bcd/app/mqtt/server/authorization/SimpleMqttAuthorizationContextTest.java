package cn.bcd.app.mqtt.server.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "mqtt.server.enabled=false",
        "mqtt.server.authorization.type=simple",
        "mqtt.server.authorization.simple.rules[0].username=device",
        "mqtt.server.authorization.simple.rules[0].publish-topic-filters[0]=devices/+/telemetry/#",
        "mqtt.server.authorization.simple.rules[0].subscribe-topic-filters[0]=devices/+/commands/#",
        "mqtt.server.persistence.retained-message.sqlite.database-path=:memory:"
})
class SimpleMqttAuthorizationContextTest {

    @Autowired
    MqttAuthorizer authorizer;

    @Test
    void shouldSelectSimpleAuthorizerAndBindRules() {
        assertInstanceOf(SimpleMqttAuthorizer.class, authorizer);
        assertTrue(authorizer.authorize(new MqttAuthorizationRequest(
                MqttAuthorizationAction.PUBLISH,
                "device-a",
                "device",
                "devices/device-a/telemetry/temp")));
        assertTrue(authorizer.authorize(new MqttAuthorizationRequest(
                MqttAuthorizationAction.SUBSCRIBE,
                "device-a",
                "device",
                "devices/device-a/commands/+")));
        assertFalse(authorizer.authorize(new MqttAuthorizationRequest(
                MqttAuthorizationAction.SUBSCRIBE,
                "device-a",
                "device",
                "devices/+/telemetry/#")));
    }
}
