package cn.bcd.lib.spring.database.jdbc.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public abstract class BaseBean extends SuperBaseBean{
    // 创建时间
    public Date createTime;

    // 创建人id
    public Long createUserId;

    // 创建人姓名
    public String createUserName;

    // 更新时间
    public Date updateTime;

    // 更新人id
    public Long updateUserId;

    // 更新人姓名
    public String updateUserName;
}
