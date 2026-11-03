package cn.bcd.app.bp.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Date;

/** 用户筛选条件，不包含分页参数和审计信息。 */
@Getter
@Setter
public class UserQueryRequest {
    @Schema(description = "主键")
    public Long id;

    @Schema(description = "用户名")
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

    @Schema(description = "生日开始")
    public Date birthdayBegin;
    @Schema(description = "生日结束")
    public Date birthdayEnd;

    @Schema(description = "身份证号")
    @Size(max = 20)
    public String cardNumber;

    @Schema(description = "是否可用(0:禁用,1:可用)")
    public Integer status;

}
