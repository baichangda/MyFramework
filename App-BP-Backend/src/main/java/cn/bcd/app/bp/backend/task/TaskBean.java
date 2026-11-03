package cn.bcd.app.bp.backend.task;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 集群任务的持久化记录，既描述待执行任务，也保存执行结果。
 * 创建信息继承 BaseBean，由 insert 自动填充；payload 提交后不再由执行线程改写。
 * 执行更新必须指定字段，不能整行保存旧对象而覆盖外部线程写入的 stopRequested。
 */
@Getter
@Setter
@Table("t_sys_task")
public class TaskBean extends BaseBean {
    // 任务名称
    public String name;

    /** 状态码见 TaskStatus；领取前由条件更新竞争，领取后由原 Worker 保存终态。 */
    // 任务状态(1:等待中;2:执行中;3:执行成功;4:执行失败;5:任务被取消;6:任务被终止)
    public int status;

    // 任务类型，例如 export
    public String type;

    /** 只保存业务参数，不保存 Service、Condition 对象或预先查询出的业务数据。 */
    // 任务参数(JSON)，由对应类型的 Handler 解析
    public String payload;

    /** 领取成功时写入的进程标识，仅供排查，不代表租约或自动恢复机制。 */
    // 领取节点，用于宕机后的人工排查
    public String nodeId;

    /** 停止请求方写入，承接领取与登记本机上下文之间的通知窗口；它不是最终停止状态。 */
    // 停止意图；执行数据更新不能覆盖此字段
    public boolean stopRequested;

    // 任务信息(失败时记录失败原因)
    public String message;

    // 任务处理进度
    public float percent;

    /** 实际记录领取成功的时间，可能早于业务方法开始。 */
    // 任务开始时间
    public Date startTime;

    // 任务完成时间
    public Date finishTime;

    // 任务执行结果(不同的任务类型结果不同、记录导出任务这里存储的是导出文件在文件服务器的路径)
    public String result;

    public TaskBean(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public TaskBean() {

    }

}
