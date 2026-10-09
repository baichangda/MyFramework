package cn.bcd.lib.spring.data.notify.subscribeNotify;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListenerInfoTest {

    @Test
    void serializeClientIdAndTimestamp() throws Exception {
        ListenerInfo listenerInfo = new ListenerInfo("order-1", "client-1", 123L, ignored -> {
        });

        ListenerInfo restored = ListenerInfo.fromString(listenerInfo.toString());

        assertEquals("order-1", restored.id);
        assertEquals("client-1", restored.clientId);
        assertEquals(123L, restored.ts);
        assertNull(restored.consumer);
    }

    @Test
    void redisFieldSeparatesClientIdAndId() {
        ListenerInfo first = new ListenerInfo("bc", "a", 1L);
        ListenerInfo second = new ListenerInfo("c", "ab", 1L);

        assertNotEquals(first.redisField(), second.redisField());
    }

    @Test
    void removingOneClientKeepsOtherClientSubscription() {
        Map<String, Set<String>> cache = new HashMap<>();
        ListenerInfo first = new ListenerInfo("order-1", "client-1", 1L);
        ListenerInfo second = new ListenerInfo("order-1", "client-2", 1L);

        AbstractNotifyServer.addListener(cache, first);
        AbstractNotifyServer.addListener(cache, second);
        AbstractNotifyServer.removeListener(cache, first);

        assertEquals(Set.of("client-2"), cache.get("order-1"));

        AbstractNotifyServer.removeListener(cache, second);

        assertFalse(cache.containsKey("order-1"));
    }
}
