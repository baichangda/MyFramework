package cn.bcd.app.tool.stock.business.bean;

import cn.bcd.app.tool.stock.base.support_eastmoney.CashFlowData;
import cn.bcd.lib.spring.database.jdbc.anno.Table;
import cn.bcd.lib.spring.database.jdbc.bean.SuperBaseBean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

/**
 * 资金流向
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

    @Schema(description = "天", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 10)
    @NotBlank(message = "[天]不能为空")
    @Size(max = 10, message = "[天]长度不能超过10")
    public String day;

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

    public static CashFlowBean from(String code, CashFlowData data) {
        CashFlowBean bean = new CashFlowBean();
        bean.code = code;
        bean.minute = data.minute;
        bean.day = data.minute.substring(0, 10);
        bean.d1 = data.d1;
        bean.d2 = data.d2;
        bean.d3 = data.d3;
        bean.d4 = data.d4;
        bean.d5 = data.d5;
        return bean;
    }

}
