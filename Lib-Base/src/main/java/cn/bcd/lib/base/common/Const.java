package cn.bcd.lib.base.common;

/**
 * 用于定义模块共享的常量
 */
public class Const {

    public final static boolean logEnable = true;

    //redis key
    public final static String redis_key_prefix_vehicle_last_packet_time = "vlpt:";
    public final static int vehicle_offline_max_time_second = 30;
}
