package cn.bcd.app.bp.backend.role;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色表
 */
@Getter
@Setter
@Table("t_sys_role")
public class RoleBean extends BaseBean {

    //field
    // 角色名称
    public String name;

    // 编码
    public String code;

    // 备注
    public String remark;
}
