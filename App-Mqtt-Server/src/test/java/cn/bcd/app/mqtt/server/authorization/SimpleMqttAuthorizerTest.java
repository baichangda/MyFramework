package cn.bcd.app.mqtt.server.authorization;

import cn.bcd.app.mqtt.server.config.MqttAuthorizationProperties;
import cn.bcd.lib.base.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleMqttAuthorizerTest {

    @Test
    void shouldAllowOnlyMatchingIdentityActionAndTopic() {
        MqttAuthorizationProperties properties = new MqttAuthorizationProperties();
        MqttAuthorizationProperties.Rule rule = new MqttAuthorizationProperties.Rule();
        rule.setUsername("device");
        rule.setClientId("device-a");
        rule.setPublishTopicFilters(List.of("devices/device-a/telemetry/#"));
        rule.setSubscribeTopicFilters(List.of("devices/device-a/commands/#"));
        properties.getSimple().setRules(List.of(rule));
        MqttAuthorizer authorizer = new SimpleMqttAuthorizer(properties);

        assertTrue(authorizer.authorize(request(
                MqttAuthorizationAction.PUBLISH,
                "device-a",
                "device",
                "devices/device-a/telemetry/temp")));
        assertTrue(authorizer.authorize(request(
                MqttAuthorizationAction.SUBSCRIBE,
                "device-a",
                "device",
                "devices/device-a/commands/+")));
        assertFalse(authorizer.authorize(request(
                MqttAuthorizationAction.SUBSCRIBE,
                "device-a",
                "device",
                "devices/+/commands/#")));
        assertFalse(authorizer.authorize(request(
                MqttAuthorizationAction.PUBLISH,
                "device-b",
                "device",
                "devices/device-a/telemetry/temp")));
        assertFalse(authorizer.authorize(request(
                MqttAuthorizationAction.PUBLISH,
                "device-a",
                "other",
                "devices/device-a/telemetry/temp")));
    }

    @Test
    void shouldRejectInvalidConfiguredTopicFilter() {
        MqttAuthorizationProperties properties = new MqttAuthorizationProperties();
        MqttAuthorizationProperties.Rule rule = new MqttAuthorizationProperties.Rule();
        rule.setPublishTopicFilters(List.of("invalid/#/filter"));
        properties.getSimple().setRules(List.of(rule));

        assertThrows(BaseException.class, () -> new SimpleMqttAuthorizer(properties));
    }

    private static MqttAuthorizationRequest request(
            MqttAuthorizationAction action,
            String clientId,
            String username,
            String topic) {
        return new MqttAuthorizationRequest(action, clientId, username, topic);
    }
}
