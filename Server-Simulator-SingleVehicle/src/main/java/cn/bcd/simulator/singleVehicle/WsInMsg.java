package cn.bcd.simulator.singleVehicle;

/**
 * @param flag 1、连接tcp网关
 *             2、更新运行数据
 * @param data
 */
public record WsInMsg(int flag, String data) {
}
