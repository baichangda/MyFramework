package cn.bcd.lib.spring.database.mongo.bean;


import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * Created by Administrator on 2017/8/25.
 */
@Getter
@Setter
public class BaseBean extends SuperBaseBean {
    // 创建时间
    public Date createTime;

    // 创建人id
    public String createUserId;

    // 创建人姓名
    public String createUserName;

    // 更新时间
    public Date updateTime;

    // 更新人id
    public String updateUserId;

    // 更新人姓名
    public String updateUserName;
}
