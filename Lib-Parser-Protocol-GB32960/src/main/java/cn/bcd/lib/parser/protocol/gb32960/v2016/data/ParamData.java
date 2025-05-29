package cn.bcd.lib.parser.protocol.gb32960.v2016.data;

import cn.bcd.lib.parser.base.anno.data.NumVal_int;
import cn.bcd.lib.parser.base.anno.data.NumVal_short;

public class ParamData {
    // 本地存储时间周期（ms）
    public NumVal_int localStorageTimeCycle;
    // 正常状态信息上报时间周期（s）
    public NumVal_int normalReportTime;
    // 报警状态信息上报时间周期（ms）
    public NumVal_int alarmReportTime;
    // 远程平台名称长度
    public short remotePlatformNameLen;
    // 远程平台域名
    public String remotePlatformName;
    // 远程平台端口
    public NumVal_int remotePlatformPort;
    // 硬件版本
    public String terminalHardwareData;
    // 固件版本
    public String terminalSoftwareData;
    // 心跳发送周期（s）
    public NumVal_short heartbeatSendCycleData;
    // 终端响应超时时间（s）
    public NumVal_int terminalResponseTimeoutData;
    // 平台响应超时时间（s）
    public NumVal_int domainResponseTimeoutData;
    // 连续三次登人失败再次登入的间隔时间（min）
    public NumVal_short loginFailureData;
    // 公共平台名称长度
    public short publicPlatformNameLen;
    // 公共平台域名
    public String publicPlatformName;
    // 公共平台端口
    public NumVal_int publicPlatformPort;
    // 是否处于抽样检测中
    public NumVal_short samplingDetectionData;
}
