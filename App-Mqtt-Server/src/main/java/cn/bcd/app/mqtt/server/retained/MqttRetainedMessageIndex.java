package cn.bcd.app.mqtt.server.retained;

import cn.bcd.app.mqtt.server.message.MqttApplicationMessage;
import cn.bcd.lib.base.exception.BaseException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 按主题层级组织的保留消息索引，并维护消息数量与载荷字节数上限。
 *
 * <p>写锁保护树结构和资源计数的一致更新，读锁允许多个主题过滤器查询并行执行。</p>
 */
final class MqttRetainedMessageIndex {

    private static final String SINGLE_LEVEL_WILDCARD = "+";
    private static final String MULTI_LEVEL_WILDCARD = "#";

    private final Node root = new Node();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxMessages;
    private final long maxPayloadBytes;
    private int messageCount;
    private long payloadBytes;

    MqttRetainedMessageIndex() {
        this(Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    MqttRetainedMessageIndex(int maxMessages, long maxPayloadBytes) {
        this.maxMessages = maxMessages;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    MqttApplicationMessage put(MqttApplicationMessage message) {
        String[] levels = levels(message.topicName());
        lock.writeLock().lock();
        try {
            Node existing = findNode(message.topicName());
            MqttApplicationMessage previous = existing == null ? null : existing.message;
            int nextCount = previous == null ? messageCount + 1 : messageCount;
            // 覆盖消息时只计算新旧载荷差值，避免把覆盖误判为新增资源。
            long nextBytes = payloadBytes + message.payloadLength()
                    - (previous == null ? 0 : previous.payloadLength());
            if (nextCount > maxMessages || nextBytes > maxPayloadBytes) {
                throw BaseException.get("MQTT retained message limit exceeded");
            }
            Node node = root;
            for (String level : levels) {
                node = node.children.computeIfAbsent(level, key -> new Node());
            }
            node.message = message;
            messageCount = nextCount;
            payloadBytes = nextBytes;
            return previous;
        } finally {
            lock.writeLock().unlock();
        }
    }

    MqttApplicationMessage remove(String topicName) {
        String[] levels = levels(topicName);
        lock.writeLock().lock();
        try {
            List<Node> path = new ArrayList<>(levels.length + 1);
            path.add(root);
            Node node = root;
            for (String level : levels) {
                node = node.children.get(level);
                if (node == null) {
                    return null;
                }
                path.add(node);
            }
            MqttApplicationMessage previous = node.message;
            node.message = null;
            if (previous != null) {
                messageCount--;
                payloadBytes -= previous.payloadLength();
            }
            prune(path, levels);
            return previous;
        } finally {
            lock.writeLock().unlock();
        }
    }

    void restorePutFailure(
            MqttApplicationMessage failed,
            MqttApplicationMessage previous) {
        lock.writeLock().lock();
        try {
            Node node = findNode(failed.topicName());
            if (node != null && node.message == failed) {
                if (previous == null) {
                    removeWhileLocked(failed.topicName());
                } else {
                    node.message = previous;
                    payloadBytes += previous.payloadLength() - failed.payloadLength();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    void restoreDeleteFailure(
            String topicName,
            MqttApplicationMessage previous) {
        if (previous == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            Node node = findNode(topicName);
            if (node == null) {
                putWhileLocked(previous);
            } else if (node.message == null) {
                node.message = previous;
                messageCount++;
                payloadBytes += previous.payloadLength();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    List<MqttApplicationMessage> findMatching(String topicFilter) {
        String[] filters = levels(topicFilter);
        lock.readLock().lock();
        try {
            List<MqttApplicationMessage> matches = new ArrayList<>();
            // 使用显式栈遍历，避免主题层级过深导致递归栈溢出。
            ArrayDeque<Cursor> cursors = new ArrayDeque<>();
            cursors.addLast(new Cursor(root, 0));
            while (!cursors.isEmpty()) {
                Cursor cursor = cursors.removeLast();
                Node node = cursor.node();
                int depth = cursor.depth();
                if (depth == filters.length) {
                    if (node.message != null) {
                        matches.add(node.message);
                    }
                    continue;
                }

                String filter = filters[depth];
                if (MULTI_LEVEL_WILDCARD.equals(filter)) {
                    // 根节点的 # 不匹配以 $ 开头的系统主题。
                    collect(node, matches, depth == 0);
                } else if (SINGLE_LEVEL_WILDCARD.equals(filter)) {
                    node.children.forEach((level, child) -> {
                        if (depth != 0 || level.charAt(0) != '$') {
                            cursors.addLast(new Cursor(child, depth + 1));
                        }
                    });
                } else {
                    Node child = node.children.get(filter);
                    if (child != null) {
                        cursors.addLast(new Cursor(child, depth + 1));
                    }
                }
            }
            return List.copyOf(matches);
        } finally {
            lock.readLock().unlock();
        }
    }

    int size() {
        lock.readLock().lock();
        try {
            return messageCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    long payloadBytes() {
        lock.readLock().lock();
        try {
            return payloadBytes;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static void collect(
            Node start,
            List<MqttApplicationMessage> messages,
            boolean excludeSystemTopics) {
        ArrayDeque<Node> nodes = new ArrayDeque<>();
        if (start.message != null) {
            messages.add(start.message);
        }
        start.children.forEach((level, child) -> {
            if (!excludeSystemTopics || level.charAt(0) != '$') {
                nodes.addLast(child);
            }
        });
        while (!nodes.isEmpty()) {
            Node node = nodes.removeLast();
            if (node.message != null) {
                messages.add(node.message);
            }
            nodes.addAll(node.children.values());
        }
    }

    private Node findNode(String topicName) {
        Node node = root;
        for (String level : levels(topicName)) {
            node = node.children.get(level);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private void putWhileLocked(MqttApplicationMessage message) {
        Node node = root;
        for (String level : levels(message.topicName())) {
            node = node.children.computeIfAbsent(level, key -> new Node());
        }
        node.message = message;
        messageCount++;
        payloadBytes += message.payloadLength();
    }

    private void removeWhileLocked(String topicName) {
        String[] levels = levels(topicName);
        List<Node> path = new ArrayList<>(levels.length + 1);
        path.add(root);
        Node node = root;
        for (String level : levels) {
            node = node.children.get(level);
            if (node == null) {
                return;
            }
            path.add(node);
        }
        MqttApplicationMessage previous = node.message;
        node.message = null;
        if (previous != null) {
            messageCount--;
            payloadBytes -= previous.payloadLength();
        }
        prune(path, levels);
    }

    private static void prune(List<Node> path, String[] levels) {
        for (int index = path.size() - 1; index > 0; index--) {
            Node node = path.get(index);
            if (node.message != null || !node.children.isEmpty()) {
                return;
            }
            path.get(index - 1).children.remove(levels[index - 1]);
        }
    }

    private static String[] levels(String value) {
        return value.split("/", -1);
    }

    private record Cursor(Node node, int depth) {
    }

    private static final class Node {
        private final Map<String, Node> children = new HashMap<>();
        private MqttApplicationMessage message;
    }
}
