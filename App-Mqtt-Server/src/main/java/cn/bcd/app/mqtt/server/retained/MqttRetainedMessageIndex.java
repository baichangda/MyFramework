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

    /**
     * 创建使用指定消息数和载荷字节上限的索引。
     *
     * @param maxMessages 消息数量上限
     * @param maxPayloadBytes 载荷总字节上限
     */
    MqttRetainedMessageIndex(int maxMessages, long maxPayloadBytes) {
        this.maxMessages = maxMessages;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /**
     * 新增或覆盖消息，并返回同主题的旧值。
     *
     * @param message 保留消息
     */
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
                // 在改动树结构前完成资源校验，失败时索引和计数保持原样。
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

    /**
     * 删除主题消息、更新资源计数并返回旧值。
     *
     * @param topicName 主题名
     */
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

    /**
     * 在数据库保存失败后恢复内存索引的保存前状态。
     *
     * @param failed 保存失败的新消息
     * @param previous 保存前的旧消息
     */
    void restorePutFailure(
            MqttApplicationMessage failed,
            MqttApplicationMessage previous) {
        lock.writeLock().lock();
        try {
            Node node = findNode(failed.topicName());
            if (node != null && node.message == failed) {
                // 仅回滚仍指向本次失败消息的节点，避免覆盖更晚的成功更新。
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

    /**
     * 在数据库删除失败后将旧消息恢复到内存索引。
     *
     * @param topicName 删除失败的主题名
     * @param previous 删除前的旧消息
     */
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
                // 删除后路径可能已被裁剪，需要重新创建完整主题路径。
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

    /**
     * 使用主题树查找过滤器匹配的保留消息。
     *
     * @param topicFilter 主题过滤器
     */
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

    /** 返回当前保留消息数量。 */
    int size() {
        lock.readLock().lock();
        try {
            return messageCount;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 返回当前保留消息的载荷总字节数。 */
    long payloadBytes() {
        lock.readLock().lock();
        try {
            return payloadBytes;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 收集指定节点下的全部消息，并可排除根级系统主题。
     *
     * @param start 起始节点
     * @param messages 结果集合
     * @param excludeSystemTopics 是否排除系统主题
     */
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

    /**
     * 按完整主题名查找叶节点。
     *
     * @param topicName 主题名
     */
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

    /**
     * 在调用方持有写锁时写入消息并更新计数。
     *
     * @param message 保留消息
     */
    private void putWhileLocked(MqttApplicationMessage message) {
        Node node = root;
        for (String level : levels(message.topicName())) {
            node = node.children.computeIfAbsent(level, key -> new Node());
        }
        node.message = message;
        messageCount++;
        payloadBytes += message.payloadLength();
    }

    /**
     * 在调用方持有写锁时删除消息并裁剪主题路径。
     *
     * @param topicName 主题名
     */
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
        // 删除消息后回收仅由该主题占用的空节点。
        prune(path, levels);
    }

    /**
     * 从叶节点开始删除不含消息和子节点的空路径。
     *
     * @param path 节点路径
     * @param levels 主题层级
     */
    private static void prune(List<Node> path, String[] levels) {
        for (int index = path.size() - 1; index > 0; index--) {
            Node node = path.get(index);
            if (node.message != null || !node.children.isEmpty()) {
                return;
            }
            path.get(index - 1).children.remove(levels[index - 1]);
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
        private final Map<String, Node> children = new HashMap<>();
        private MqttApplicationMessage message;
    }
}
