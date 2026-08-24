package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import cn.bcd.app.mqtt.server.topic.MqttTopicFilter;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class MqttSubscriptionIndex {

    private final ConcurrentMap<String, Set<String>> exactSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> wildcardSubscriptions = new ConcurrentHashMap<>();

    void add(String clientId, MqttSubscription subscription) {
        ConcurrentMap<String, Set<String>> index = indexFor(subscription.topicFilter());
        index.compute(subscription.topicFilter(), (topicFilter, clientIds) -> {
            Set<String> updated = clientIds == null ? ConcurrentHashMap.newKeySet() : clientIds;
            updated.add(clientId);
            return updated;
        });
    }

    void remove(MqttSession session) {
        for (MqttSubscription subscription : session.subscriptions()) {
            ConcurrentMap<String, Set<String>> index = indexFor(subscription.topicFilter());
            index.computeIfPresent(subscription.topicFilter(), (topicFilter, clientIds) -> {
                clientIds.remove(session.clientId());
                return clientIds.isEmpty() ? null : clientIds;
            });
        }
    }

    Set<String> findSubscribers(String topicName) {
        Set<String> clientIds = new HashSet<>();
        Set<String> exactClientIds = exactSubscriptions.get(topicName);
        if (exactClientIds != null) {
            clientIds.addAll(exactClientIds);
        }
        wildcardSubscriptions.forEach((topicFilter, wildcardClientIds) -> {
            if (MqttTopicFilter.matches(topicFilter, topicName)) {
                clientIds.addAll(wildcardClientIds);
            }
        });
        return clientIds;
    }

    private ConcurrentMap<String, Set<String>> indexFor(String topicFilter) {
        return topicFilter.indexOf('+') >= 0 || topicFilter.indexOf('#') >= 0
                ? wildcardSubscriptions
                : exactSubscriptions;
    }
}
