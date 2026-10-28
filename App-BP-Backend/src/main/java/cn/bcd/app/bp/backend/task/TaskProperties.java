package cn.bcd.app.bp.backend.task;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * task 前缀下的节点配置。各节点可以设置不同并发和消费类型，
 * 但连接同一任务表的节点应使用相同的 Redis 停止 topic。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "task")
public class TaskProperties {
    /** 所有类型共享的节点执行并发数。 */
    private int concurrency = 2;
    /** 留空表示消费全部已注册类型；显式配置的每个类型都必须有本地 Handler。 */
    private List<String> types = List.of();
    /**
     * 连续空闲时逐级增加等待时间，停留在最后一级；领取成功后回到第一级。
     * 每个 Worker 独立计数，并增加少量随机抖动，因此最大值也影响新任务发现延迟。
     */
    private List<Duration> pollIntervals = List.of(Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
    /** 领取时保存到数据库。默认每次启动生成独立标识；手动配置时应保证集群内可区分。 */
    private String nodeId = System.getenv().getOrDefault("COMPUTERNAME", "node") + "-" + UUID.randomUUID();
    /** 同一任务表的所有节点必须使用同一个 topic。 */
    private String stopTopic = "task-stop";

    /** 创建 Worker 时执行一次基础校验；类型是否已注册由 Worker 进一步检查。 */
    void validate() {
        if (concurrency < 1 || nodeId == null || nodeId.isBlank() || nodeId.length() > 128
                || stopTopic == null || stopTopic.isBlank() || types == null
                || pollIntervals == null || pollIntervals.isEmpty()) {
            throw new IllegalArgumentException("Invalid task configuration");
        }
        long previous = 0;
        for (Duration interval : pollIntervals) {
            if (interval == null || interval.toMillis() < 1 || interval.toMillis() < previous) {
                throw new IllegalArgumentException("task.poll-intervals must be positive and ascending");
            }
            previous = interval.toMillis();
        }
    }
}
