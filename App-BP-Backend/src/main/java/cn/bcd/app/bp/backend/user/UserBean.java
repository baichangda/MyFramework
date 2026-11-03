package cn.bcd.app.bp.backend.user;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import cn.bcd.lib.spring.database.jdbc.bean.UserInterface;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    // 用户名
    public String username;

    @JsonIgnore
    // 密码
    public String password;

    // 邮箱
    public String email;

    // 手机号
    public String phone;

    // 真实姓名
    public String realName;

    // 性别
    public String sex;

    // 生日
    public Date birthday;

    // 身份证号
    public String cardNumber;

    // 是否可用(0:禁用,1:可用)
    public Integer status;

}
