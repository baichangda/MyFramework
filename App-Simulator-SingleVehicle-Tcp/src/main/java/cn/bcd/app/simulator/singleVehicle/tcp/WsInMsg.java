package cn.bcd.app.simulator.singleVehicle.tcp;

/**
 * @param flag 1、连接tcp网关
 *             2、更新车辆数据
 * @param data
 */
public record WsInMsg(int flag, String data) {
}
