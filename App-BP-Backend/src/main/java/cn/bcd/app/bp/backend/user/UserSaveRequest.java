package cn.bcd.app.bp.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Date;

/** 用户完整编辑表单；可空字段缺失或为 null 时表示清空。 */
@Getter
@Setter
public class UserSaveRequest {
    @Positive
    @Schema(description = "主键；新增时不传，修改时必填")
    public Long id;

    @Schema(description = "用户名")
    @NotBlank
    @Size(max = 50)
    public String username;

    @Schema(description = "邮箱")
    @Size(max = 100)
    public String email;

    @Schema(description = "手机号")
    @Size(max = 11)
    public String phone;

    @Schema(description = "真实姓名")
    @Size(max = 50)
    public String realName;

    @Schema(description = "性别")
    @Size(max = 5)
    public String sex;

    @Schema(description = "生日")
    public Date birthday;

    @Schema(description = "身份证号")
    @Size(max = 20)
    public String cardNumber;

    @Schema(description = "是否可用(0:禁用,1:可用)")
    @NotNull
    @Min(0)
    @Max(1)
    public Integer status;

}
