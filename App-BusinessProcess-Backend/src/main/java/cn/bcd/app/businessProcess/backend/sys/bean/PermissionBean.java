package cn.bcd.app.businessProcess.backend.sys.bean;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 权限
 */
@Getter
@Setter
@Table("t_sys_permission")
public class PermissionBean extends BaseBean {
    @Serial
    private final static long serialVersionUID = 1L;

    //field
    @Schema(description = "权限名称", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20)
    @NotBlank(message = "[权限名称]不能为空")
    @Size(max = 20, message = "[权限名称]长度不能超过20")
    public String name;

    @Schema(description = "资源", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    @NotBlank(message = "[资源]不能为空")
    @Size(max = 100, message = "[资源]长度不能超过100")
    public String resource;

}
