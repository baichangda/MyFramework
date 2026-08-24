package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(
        prefix = "mqtt.server.persistence.session",
        name = "type",
        havingValue = "memory")
public final class InMemoryMqttSessionStore implements MqttSessionStore {

    private final ConcurrentMap<String, MqttSessionSnapshot> sessions =
            new ConcurrentHashMap<>();

    @Override
    public synchronized Collection<MqttSessionSnapshot> loadAll() {
        return List.copyOf(sessions.values());
    }

    @Override
    public synchronized void save(MqttSessionSnapshot session) {
        sessions.put(session.clientId(), session);
    }

    @Override
    public synchronized void delete(String clientId) {
        sessions.remove(clientId);
    }
}
