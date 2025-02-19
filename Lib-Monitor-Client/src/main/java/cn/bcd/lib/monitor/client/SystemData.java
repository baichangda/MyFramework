package cn.bcd.lib.monitor.client;


import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 系统监控信息表
 */
@Getter
@Setter
public class SystemData {
    //field
    //cpu物理核心
    public int physicalProcessorNum;

    //cpu逻辑核心
    public int logicalProcessorNum;

    //cpu使用百分比
    public double cpuUsePercent;

    //内存使用百分比
    public double memoryUsePercent;

    //最大内存(GB)
    public double memoryMax;

    //已使用内存(GB)
    public double memoryUse;

    //磁盘最大容量(GB)
    public double diskMax;

    //磁盘使用容量(GB)
    public double diskUse;

    //磁盘使用百分比
    public double diskUsePercent;

    //磁盘读取速度(KB/s)
    public double diskReadSpeed;

    //磁盘写入速度(KB/s)
    public double diskWriteSpeed;

    //网络流入速度(KB/s)
    public double netRecvSpeed;

    //网络流出速度(KB/s)
    public double netSentSpeed;

    //method

}
