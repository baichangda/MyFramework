package cn.bcd.app.bp.backend.permission;

import cn.bcd.lib.spring.database.common.condition.Condition;
import cn.bcd.lib.spring.database.common.condition.impl.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Date;

/** 权限筛选条件，不包含分页参数和审计信息。 */
@Getter
@Setter
public class PermissionQueryRequest {
    @Schema(description = "id")
    public Long id;

    @Schema(description = "权限名称")
    @Size(max = 20)
    public String name;

    @Schema(description = "资源")
    @Size(max = 100)
    public String resource;

    public Condition toCondition() {
        return Condition.and(
                NumberCondition.EQUAL("id", this.id),
                StringCondition.EQUAL("name", this.name),
                StringCondition.EQUAL("resource", this.resource)
        );
    }
}
