package cn.bcd.app.tool.stock.business.bean;

import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.anno.Unique;
import cn.bcd.lib.spring.database.jdbc.bean.BaseBean;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import java.io.Serial;

/**
 *  资金流向
 */
@Getter
@Setter
@Table("t_cash_flow")
public class CashFlowBean extends SuperBaseBean {
    @Serial
    private final static long serialVersionUID = 1L;

    //field
    @Schema(description = "股票号码", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20)
    @NotBlank(message = "[股票号码]不能为空")
    @Size(max = 20, message = "[股票号码]长度不能超过20")
    public String code;

    @Schema(description = "分钟时间", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20)
    @NotBlank(message = "[分钟时间]不能为空")
    @Size(max = 20, message = "[分钟时间]长度不能超过20")
    public String minute;

    @Schema(description = "主力净流入", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "[主力净流入]不能为空")
    public Double d1;

    @Schema(description = "超大单净流入", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "[超大单净流入]不能为空")
    public Double d2;

    @Schema(description = "大单净流入", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "[大单净流入]不能为空")
    public Double d3;

    @Schema(description = "中单净流入", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "[中单净流入]不能为空")
    public Double d4;

    @Schema(description = "小单净流入", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "[小单净流入]不能为空")
    public Double d5;

}
