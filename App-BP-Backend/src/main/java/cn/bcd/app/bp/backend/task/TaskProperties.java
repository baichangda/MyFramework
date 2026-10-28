package cn.bcd.app.bp.backend.task;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "task")
public class TaskProperties {
    /** 所有类型共享的节点执行并发数。 */
    private int concurrency = 2;
    /** 留空表示消费本节点注册的全部类型。 */
    private List<String> types = List.of();
    private List<Duration> pollIntervals = List.of(Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
    /** 每次启动生成独立标识，也可配置为便于运维识别的名称。 */
    private String nodeId = System.getenv().getOrDefault("COMPUTERNAME", "node") + "-" + UUID.randomUUID();
    /** 同一任务表的所有节点必须使用同一个 topic。 */
    private String stopTopic = "task-stop";

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
