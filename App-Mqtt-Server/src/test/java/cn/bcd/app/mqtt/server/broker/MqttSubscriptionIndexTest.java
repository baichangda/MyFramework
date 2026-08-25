package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.session.MqttSubscription;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttSubscriptionIndexTest {

    @Test
    void shouldMatchExactSingleAndMultiLevelSubscriptions() {
        MqttSubscriptionIndex index = new MqttSubscriptionIndex();
        index.add("exact", subscription("sensor/room/temp", MqttQoS.AT_MOST_ONCE));
        index.add("single", subscription("sensor/+/temp", MqttQoS.AT_LEAST_ONCE));
        index.add("multi", subscription("sensor/#", MqttQoS.EXACTLY_ONCE));

        Map<String, MqttQoS> subscribers = index.findSubscribers("sensor/room/temp");

        assertEquals(MqttQoS.AT_MOST_ONCE, subscribers.get("exact"));
        assertEquals(MqttQoS.AT_LEAST_ONCE, subscribers.get("single"));
        assertEquals(MqttQoS.EXACTLY_ONCE, subscribers.get("multi"));
        assertEquals(Map.of("multi", MqttQoS.EXACTLY_ONCE),
                index.findSubscribers("sensor"));
    }

    @Test
    void shouldReturnMaximumQosForOverlappingClientSubscriptions() {
        MqttSubscriptionIndex index = new MqttSubscriptionIndex();
        index.add("cid", subscription("sensor/room/temp", MqttQoS.AT_MOST_ONCE));
        index.add("cid", subscription("sensor/+/temp", MqttQoS.EXACTLY_ONCE));
        index.add("cid", subscription("sensor/#", MqttQoS.AT_LEAST_ONCE));

        assertEquals(
                Map.of("cid", MqttQoS.EXACTLY_ONCE),
                index.findSubscribers("sensor/room/temp"));
    }

    @Test
    void shouldKeepRootWildcardsSeparateFromSystemTopics() {
        MqttSubscriptionIndex index = new MqttSubscriptionIndex();
        index.add("root-multi", subscription("#", MqttQoS.AT_LEAST_ONCE));
        index.add("root-single", subscription("+/broker/uptime", MqttQoS.AT_LEAST_ONCE));
        index.add("system", subscription("$SYS/#", MqttQoS.EXACTLY_ONCE));

        Map<String, MqttQoS> subscribers = index.findSubscribers("$SYS/broker/uptime");

        assertEquals(Map.of("system", MqttQoS.EXACTLY_ONCE), subscribers);
    }

    @Test
    void shouldReplaceQosAndRemoveUnusedBranches() {
        MqttSubscriptionIndex index = new MqttSubscriptionIndex();
        index.add("cid", subscription("sensor/+/temp", MqttQoS.AT_MOST_ONCE));
        index.add("cid", subscription("sensor/+/temp", MqttQoS.EXACTLY_ONCE));
        assertEquals(MqttQoS.EXACTLY_ONCE,
                index.findSubscribers("sensor/room/temp").get("cid"));

        index.remove("cid", "sensor/+/temp");

        assertTrue(index.findSubscribers("sensor/room/temp").isEmpty());
        assertFalse(index.findSubscribers("sensor").containsKey("cid"));
    }

    private static MqttSubscription subscription(String filter, MqttQoS qos) {
        return new MqttSubscription(filter, qos);
    }
}
