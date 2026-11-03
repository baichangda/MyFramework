package cn.bcd.app.bp.backend.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Date;

/** 权限完整编辑表单；可空字段缺失或为 null 时表示清空。 */
@Getter
@Setter
public class PermissionSaveRequest {
    @Positive
    @Schema(description = "主键；新增时不传，修改时必填")
    public Long id;

    @Schema(description = "权限名称")
    @NotBlank
    @Size(max = 20)
    public String name;

    @Schema(description = "资源")
    @NotBlank
    @Size(max = 100)
    public String resource;

}
