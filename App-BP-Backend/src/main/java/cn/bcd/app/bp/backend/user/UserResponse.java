package cn.bcd.app.bp.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Date;

/** 用户接口返回模型，与数据库实体独立。 */
public class UserResponse {
    @Schema(description = "主键")
    public Long id;

    @Schema(description = "用户名")
    public String username;

    @Schema(description = "邮箱")
    public String email;

    @Schema(description = "手机号")
    public String phone;

    @Schema(description = "真实姓名")
    public String realName;

    @Schema(description = "性别")
    public String sex;

    @Schema(description = "生日")
    public Date birthday;

    @Schema(description = "身份证号")
    public String cardNumber;

    @Schema(description = "是否可用(0:禁用,1:可用)")
    public Integer status;

    @Schema(description = "创建时间")
    public Date createTime;

    @Schema(description = "创建人id")
    public Long createUserId;

    @Schema(description = "创建人姓名")
    public String createUserName;

    @Schema(description = "更新时间")
    public Date updateTime;

    @Schema(description = "更新人id")
    public Long updateUserId;

    @Schema(description = "更新人姓名")
    public String updateUserName;

}
