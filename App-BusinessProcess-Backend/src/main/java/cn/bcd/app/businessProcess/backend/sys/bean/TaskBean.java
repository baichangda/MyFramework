package cn.bcd.app.businessProcess.backend.sys.bean;

import cn.bcd.lib.base.util.ExceptionUtil;
import cn.bcd.lib.database.jdbc.anno.Table;
import cn.bcd.lib.database.jdbc.bean.SuperBaseBean;
import cn.bcd.app.businessProcess.backend.base.support_satoken.SaTokenUtil;
import cn.bcd.app.businessProcess.backend.base.support_task.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 系统任务处理表
 */
@Getter
@Setter
@Table("t_sys_task")
public class TaskBean extends SuperBaseBean implements Task<Long> {
    //field
    @NotBlank(message = "[任务名称]不能为空")
    @Size(max = 50, message = "[任务名称]长度不能超过50")
    @Schema(description = "任务名称", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    public String name;

    @NotNull(message = "[任务状态]不能为空")
    @Schema(description = "任务状态(1:等待中;2:执行中;3:执行成功;4:执行失败;5:任务被取消;:6:任务被终止)", requiredMode = Schema.RequiredMode.REQUIRED)
    public int status;

    @Schema(description = "任务类型(1:导出任务)", requiredMode = Schema.RequiredMode.REQUIRED)
    public int type;

    @Size(max = 255, message = "[任务信息]长度不能超过255")
    @Schema(description = "任务信息(失败时记录失败原因)", maxLength = 255)
    public String message;

    @NotNull(message = "[任务处理进度]")
    @Schema(description = "任务处理进度", requiredMode = Schema.RequiredMode.REQUIRED)
    public float percent;

    @Schema(description = "任务开始时间")
    public Date startTime;

    @Schema(description = "任务完成时间")
    public Date finishTime;

    @Schema(description = "创建时间")
    public Date createTime;

    @Size(max = 255, message = "[任务执行结果]长度不能超过255")
    @Schema(description = "任务执行结果(不同的任务类型结果不同、记录导出任务这里存储的是导出文件在文件服务器的路径)", maxLength = 255)
    public String result;

    @Schema(description = "创建人id")
    public Long createUserId;

    @Size(max = 50, message = "[创建人姓名]长度不能超过50")
    @Schema(description = "创建人姓名", maxLength = 50)
    public String createUserName;

    public TaskBean(String name, int type) {
        this.name = name;
        this.type = type;
    }

    public TaskBean() {

    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public void onCreated() {
        createTime = new Date();
        UserBean userBean = SaTokenUtil.getLoginUser_cache();
        if (userBean != null) {
            createUserId = userBean.getId();
            createUserName = userBean.realName;
        }
    }

    @Override
    public void onStarted() {
        startTime = new Date();
    }

    @Override
    public void onSucceed() {
        finishTime = new Date();
        percent = 100F;
    }

    @Override
    public void onFailed(Exception ex) {
        finishTime = new Date();
        Throwable realException = ExceptionUtil.getRealException(ex);
        message = realException.getMessage();
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public void onStopped() {
        finishTime = new Date();
    }

    @Override
    public Long getId() {
        return id;
    }
}
