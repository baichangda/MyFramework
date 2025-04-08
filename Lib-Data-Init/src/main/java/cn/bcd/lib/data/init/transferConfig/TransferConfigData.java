package cn.bcd.lib.data.init.transferConfig;

import lombok.Data;

@Data
public class TransferConfigData {

    // @Schema(description = "平台名称", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
    public String platName;

    //  @Schema(description = "唯一编码", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
    public String platCode;

    //   @Schema(description = "上报地址", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
    public String upAddress;

    // @Schema(description = "上报端口", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer upPort;

    // @Schema(description = "上报频率(s)", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer upFrequency;

    //  @Schema(description = "企业用户名", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
    public String enterpriseName;

    // @Schema(description = "服务器主机名")
    public String serverId;

    // @Schema(description = "服务器地址")
    public String serverAddress;

    // @Schema(description = "平台密码")
    public String password;

    //  @Schema(description = "kafka分区")
    public String kafkaPartition;

    //  @Schema(description = "唯一标识")
    public String uniqueCode;

    //  @Schema(description = "补发阈值")
    public Integer reissueTimeThreshold;

    //  @Schema(description = "是否转发报警(0.否,1.是)")
    public Integer transferAlarm;

}
