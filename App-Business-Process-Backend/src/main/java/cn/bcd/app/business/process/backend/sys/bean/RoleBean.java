package cn.bcd.app.business.process.backend.sys.bean;

import cn.bcd.lib.database.jdbc.anno.Table;
import cn.bcd.lib.database.jdbc.bean.BaseBean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色表
 */
@Getter
@Setter
@Table("t_sys_role")
public class RoleBean extends BaseBean {

    //field
    @NotBlank(message = "[角色名称]不能为空")
    @Size(max = 20, message = "[角色名称]长度不能超过20")
    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20)
    public String name;

    @NotBlank(message = "[编码]不能为空")
    @Size(max = 50, message = "[编码]长度不能超过50")
    @Schema(description = "编码", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
    public String code;

    @Size(max = 256, message = "[备注]长度不能超过256")
    @Schema(description = "备注", maxLength = 256)
    public String remark;
}
