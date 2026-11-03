package cn.bcd.app.bp.backend.permission;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import io.swagger.v3.oas.annotations.media.Schema;
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
    public String name;

    @Schema(description = "资源", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    public String resource;

}
