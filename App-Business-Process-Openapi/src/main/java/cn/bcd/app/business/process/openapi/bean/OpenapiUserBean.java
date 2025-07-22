package cn.bcd.app.business.process.openapi.bean;

import cn.bcd.lib.database.jdbc.anno.Table;
import cn.bcd.lib.database.jdbc.bean.BaseBean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.io.Serial;

/**
 *  openapi用户
 */
@Getter
@Setter
@Table("t_openapi_user")
public class OpenapiUserBean extends BaseBean {
    @Serial
    private final static long serialVersionUID = 1L;

    //field
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
    @NotBlank(message = "[用户名]不能为空")
    @Size(max = 50, message = "[用户名]长度不能超过50")
    public String name;

    @Schema(description = "用户状态(1:启用;0:禁用)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "[用户状态]不能为空")
    public Integer status;

    @Schema(description = "密钥", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
    @NotBlank(message = "[密钥]不能为空")
    @Size(max = 32, message = "[密钥]长度不能超过32")
    public String secretKey;

    @Schema(description = "备注", maxLength = 100)
    @Size(max = 100, message = "[备注]长度不能超过100")
    public String remark;

}
