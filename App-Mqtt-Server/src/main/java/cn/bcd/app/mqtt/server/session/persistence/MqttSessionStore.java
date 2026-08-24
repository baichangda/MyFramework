package cn.bcd.app.mqtt.server.session.persistence;

import cn.bcd.app.mqtt.server.session.MqttSessionSnapshot;

import java.util.Collection;

public interface MqttSessionStore {

    Collection<MqttSessionSnapshot> loadAll();

    void save(MqttSessionSnapshot session);

    void delete(String clientId);
}
