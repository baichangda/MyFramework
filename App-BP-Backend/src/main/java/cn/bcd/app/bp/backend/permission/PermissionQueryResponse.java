package cn.bcd.app.bp.backend.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Date;

/** 权限接口返回模型，与数据库实体独立。 */
public class PermissionQueryResponse {
    @Schema(description = "主键")
    public Long id;

    @Schema(description = "权限名称")
    public String name;

    @Schema(description = "资源")
    public String resource;

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

    public static PermissionQueryResponse fromBean(PermissionBean bean) {
        PermissionQueryResponse queryResponse = new PermissionQueryResponse();
        queryResponse.id = bean.id;
        queryResponse.name = bean.name;
        queryResponse.resource = bean.resource;
        queryResponse.createTime = bean.createTime;
        queryResponse.createUserId = bean.createUserId;
        queryResponse.createUserName = bean.createUserName;
        queryResponse.updateTime = bean.updateTime;
        queryResponse.updateUserId = bean.updateUserId;
        queryResponse.updateUserName = bean.updateUserName;
        return queryResponse;
    }
}
