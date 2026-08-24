package cn.bcd.app.mqtt.server.connection;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MqttConnectionRegistry {

    private final ConcurrentMap<String, MqttConnectionRegistration> registrations = new ConcurrentHashMap<>();
    private final AtomicLong generationSequence = new AtomicLong();

    public MqttConnectionRegistration register(String clientId, MqttConnection connection) {
        MqttConnectionRegistration registration = new MqttConnectionRegistration(
                clientId, connection, generationSequence.incrementAndGet());
        connection.registration(registration);

        AtomicReference<MqttConnectionRegistration> previousReference = new AtomicReference<>();
        registrations.compute(clientId, (key, previous) -> {
            previousReference.set(previous);
            return registration;
        });

        MqttConnectionRegistration previous = previousReference.get();
        if (previous != null && previous.connection() != connection) {
            previous.connection().close(MqttConnectionCloseReason.CONNECTION_TAKEN_OVER);
        }
        return registration;
    }

    public void unregister(MqttConnection connection) {
        MqttConnectionRegistration registration = connection.registration();
        if (registration != null) {
            registrations.remove(registration.clientId(), registration);
        }
    }

    public Optional<MqttConnection> findConnection(String clientId) {
        MqttConnectionRegistration registration = registrations.get(clientId);
        return registration == null ? Optional.empty() : Optional.of(registration.connection());
    }

    public int size() {
        return registrations.size();
    }
}
