package cn.bcd.app.bp.backend.task.export;

import cn.bcd.lib.spring.database.common.condition.Condition;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Sort;

/**
 * 提交时构建的导出查询参数，任务框架负责 JSON 持久化及还原。
 * condition 为 null 时不限制查询；分批导出应指定稳定排序，例如主键升序。
 */
public final class ExportPayload {
    public final Condition condition;
    public final ExportSort sort;

    public ExportPayload(Condition condition, Sort sort) {
        this(condition, ExportSort.fromSort(sort));
    }

    @JsonCreator
    private ExportPayload(@JsonProperty("condition") Condition condition,
                          @JsonProperty("sort") ExportSort sort) {
        this.condition = condition;
        this.sort = sort == null ? ExportSort.fromSort(null) : sort;
    }
}
