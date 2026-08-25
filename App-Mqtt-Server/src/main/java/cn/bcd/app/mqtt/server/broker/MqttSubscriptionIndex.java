package cn.bcd.app.mqtt.server.broker;

import cn.bcd.app.mqtt.server.session.MqttSession;
import cn.bcd.app.mqtt.server.session.MqttSubscription;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** 基于主题层级树的并发订阅索引，支持 {@code +} 和 {@code #} 通配符。 */
final class MqttSubscriptionIndex {

    private static final String SINGLE_LEVEL_WILDCARD = "+";
    private static final String MULTI_LEVEL_WILDCARD = "#";

    private final Node root = new Node();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 将单条订阅加入主题树。
     *
     * @param clientId 客户端标识
     * @param subscription 订阅内容
     */
    void add(String clientId, MqttSubscription subscription) {
        String[] levels = levels(subscription.topicFilter());
        lock.writeLock().lock();
        try {
            Node node = root;
            for (String level : levels) {
                if (MULTI_LEVEL_WILDCARD.equals(level)) {
                    node.multiLevelSubscribers.put(clientId, subscription.qos());
                    return;
                }
                node = SINGLE_LEVEL_WILDCARD.equals(level)
                        ? node.singleLevelChild()
                        : node.literalChildren.computeIfAbsent(level, key -> new Node());
            }
            node.subscribers.put(clientId, subscription.qos());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 将会话中的全部订阅恢复到主题树。
     *
     * @param session 客户端会话
     */
    void add(MqttSession session) {
        for (MqttSubscription subscription : session.subscriptions()) {
            add(session.clientId(), subscription);
        }
    }

    /**
     * 从主题树移除会话的全部订阅。
     *
     * @param session 客户端会话
     */
    void remove(MqttSession session) {
        for (MqttSubscription subscription : session.subscriptions()) {
            remove(session.clientId(), subscription.topicFilter());
        }
    }

    /**
     * 删除客户端的一条主题订阅，并裁剪空节点。
     *
     * @param clientId 客户端标识
     * @param topicFilter 主题过滤器
     */
    void remove(String clientId, String topicFilter) {
        String[] levels = levels(topicFilter);
        lock.writeLock().lock();
        try {
            List<Node> path = new ArrayList<>(levels.length + 1);
            path.add(root);
            Node node = root;
            for (String level : levels) {
                if (MULTI_LEVEL_WILDCARD.equals(level)) {
                    node.multiLevelSubscribers.remove(clientId);
                    prune(path, levels);
                    return;
                }
                node = SINGLE_LEVEL_WILDCARD.equals(level)
                        ? node.singleLevel
                        : node.literalChildren.get(level);
                if (node == null) {
                    return;
                }
                path.add(node);
            }
            node.subscribers.remove(clientId);
            prune(path, levels);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 查找匹配主题名的客户端及其最高订阅 QoS。
     *
     * @param topicName 主题名
     */
    Map<String, MqttQoS> findSubscribers(String topicName) {
        String[] topicLevels = levels(topicName);
        boolean systemTopic = topicName.charAt(0) == '$';
        lock.readLock().lock();
        try {
            Map<String, MqttQoS> matches = new HashMap<>();
            // 同时沿字面量与单层通配分支搜索，多层通配订阅在经过每个节点时合并。
            ArrayDeque<Cursor> cursors = new ArrayDeque<>();
            cursors.addLast(new Cursor(root, 0));
            while (!cursors.isEmpty()) {
                Cursor cursor = cursors.removeLast();
                Node node = cursor.node();
                int depth = cursor.depth();
                if (!(systemTopic && depth == 0)) {
                    merge(matches, node.multiLevelSubscribers);
                }
                if (depth == topicLevels.length) {
                    merge(matches, node.subscribers);
                    continue;
                }

                Node literal = node.literalChildren.get(topicLevels[depth]);
                if (literal != null) {
                    cursors.addLast(new Cursor(literal, depth + 1));
                }
                if (node.singleLevel != null && !(systemTopic && depth == 0)) {
                    cursors.addLast(new Cursor(node.singleLevel, depth + 1));
                }
            }
            return Map.copyOf(matches);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 合并命中订阅，并为每个客户端保留最高 QoS。
     *
     * @param matches 已合并的匹配结果
     * @param subscribers 当前节点的订阅者
     */
    private static void merge(
            Map<String, MqttQoS> matches,
            Map<String, MqttQoS> subscribers) {
        // 一个客户端可能通过多条过滤器命中同一主题，仅保留其中最高的订阅 QoS。
        subscribers.forEach((clientId, qos) -> matches.merge(
                clientId,
                qos,
                (current, candidate) -> current.value() >= candidate.value()
                        ? current
                        : candidate));
    }

    /**
     * 从叶节点向根节点裁剪不再承载订阅的空路径。
     *
     * @param path 节点路径
     * @param levels 过滤器层级
     */
    private static void prune(
            List<Node> path,
            String[] levels) {
        for (int index = path.size() - 1; index > 0; index--) {
            Node node = path.get(index);
            if (!node.isEmpty()) {
                return;
            }
            Node parent = path.get(index - 1);
            String level = levels[index - 1];
            if (SINGLE_LEVEL_WILDCARD.equals(level)) {
                parent.singleLevel = null;
            } else {
                parent.literalChildren.remove(level);
            }
        }
    }

    /**
     * 切分主题层级并保留空层级。
     *
     * @param value 主题名或过滤器
     */
    private static String[] levels(String value) {
        return value.split("/", -1);
    }

    private record Cursor(Node node, int depth) {
    }

    private static final class Node {
        private final Map<String, Node> literalChildren = new HashMap<>();
        private final Map<String, MqttQoS> subscribers = new HashMap<>();
        private final Map<String, MqttQoS> multiLevelSubscribers = new HashMap<>();
        private Node singleLevel;

        /** 获取或创建单层通配符子节点。 */
        private Node singleLevelChild() {
            if (singleLevel == null) {
                singleLevel = new Node();
            }
            return singleLevel;
        }

        /** 判断节点是否不包含子节点及任何订阅者。 */
        private boolean isEmpty() {
            return literalChildren.isEmpty()
                    && singleLevel == null
                    && subscribers.isEmpty()
                    && multiLevelSubscribers.isEmpty();
        }
    }
}
