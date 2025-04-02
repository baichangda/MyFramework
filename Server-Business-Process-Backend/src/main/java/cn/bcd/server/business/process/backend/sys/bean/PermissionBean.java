package cn.bcd.server.business.process.backend.sys.bean;

import cn.bcd.lib.database.jdbc.anno.Table;
import cn.bcd.lib.database.jdbc.bean.BaseBean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色与权限关系表
 */
@Getter
@Setter
@Table("t_sys_permission")
public class PermissionBean extends BaseBean {
    //field
    @NotBlank(message = "[资源]不能为空")
    @Size(max = 100, message = "[资源]长度不能超过100")
    @Schema(description = "资源", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    public String resource;

    @NotBlank(message = "[角色名称]不能为空")
    @Size(max = 20, message = "[角色名称]长度不能超过20")
    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20)
    public String name;
}
