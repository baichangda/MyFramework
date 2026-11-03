package cn.bcd.app.bp.backend.menu;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.anno.Transient;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单表
 */
@Getter
@Setter
@Table("t_sys_menu")
public class MenuBean extends BaseBean {
    //field
    // 父菜单id
    public Long parentId;

    // 菜单名称
    public String name;

    // url地址
    public String url;

    // 图标
    public String icon;

    // 排序
    public Integer orderNum;

    @Transient
    public List<MenuBean> children = new ArrayList<>();
}
