package cn.bcd.lib.parser.protocol.gb32960.v2025.data;


public class ParamData {
    // 本地存储时间周期（ms）
    public Integer localStorageTimeCycle;
    public byte localStorageTimeCycle__type;
    // 正常状态信息上报时间周期（s）
    public Short normalReportTime;
    public byte normalReportTime__type;
    // 报警状态信息上报时间周期（ms）
    public Short alarmReportTime;
    public byte alarmReportTime__type;
    // 远程平台名称长度
    public Short remotePlatformNameLen;
    // 远程平台域名
    public String remotePlatformName;
    // 远程平台端口
    public Integer remotePlatformPort;
    public byte remotePlatformPort__type;
    // 硬件版本
    public String terminalHardwareData;
    // 固件版本
    public String terminalSoftwareData;
    // 心跳发送周期（s）
    public Short heartbeatSendCycleData;
    public byte heartbeatSendCycleData__type;
    // 终端响应超时时间（s）
    public Integer terminalResponseTimeoutData;
    public byte terminalResponseTimeoutData__type;
    // 平台响应超时时间（s）
    public Integer domainResponseTimeoutData;
    public byte domainResponseTimeoutData__type;
    // 连续三次登人失败再次登入的间隔时间（min）
    public Short loginFailureData;
    public byte loginFailureData__type;
    // 公共平台名称长度
    public short publicPlatformNameLen;
    // 公共平台域名
    public String publicPlatformName;
    // 公共平台端口
    public Integer publicPlatformPort;
    public byte publicPlatformPort__type;
    // 是否处于抽样检测中
    public Short samplingDetectionData;
    public byte samplingDetectionData__type;
}
