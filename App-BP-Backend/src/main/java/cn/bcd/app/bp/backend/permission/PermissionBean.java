package cn.bcd.app.bp.backend.permission;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import lombok.Getter;
import lombok.Setter;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/** 权限数据库实体。 */
@Getter
@Setter
@Table("t_sys_permission")
public class PermissionBean extends BaseBean {
    @Serial
    private static final long serialVersionUID = 1L;

    // 权限名称
    public String name;

    // 资源
    public String resource;

}
