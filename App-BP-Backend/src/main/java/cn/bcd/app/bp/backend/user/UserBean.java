package cn.bcd.app.bp.backend.user;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import cn.bcd.lib.spring.database.jdbc.bean.UserInterface;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.util.Date;

/**
 * 用户基础信息表
 */
@Getter
@Setter
@Table("t_sys_user")
public class UserBean extends BaseBean implements UserInterface {
    @Serial
    private final static long serialVersionUID = 1L;

    //field
    @Schema(description = "用户名", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    public String username;

    @JsonIgnore
    @Schema(description = "密码", maxLength = 100)
    public String password;

    @Schema(description = "邮箱", maxLength = 100)
    public String email;

    @Schema(description = "手机号", maxLength = 11)
    public String phone;

    @Schema(description = "真实姓名", maxLength = 50)
    public String realName;

    @Schema(description = "性别", maxLength = 5)
    public String sex;

    @Schema(description = "生日")
    public Date birthday;

    @Schema(description = "身份证号", maxLength = 20)
    public String cardNumber;

    @Schema(description = "是否可用(0:禁用,1:可用)",requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer status;

}
