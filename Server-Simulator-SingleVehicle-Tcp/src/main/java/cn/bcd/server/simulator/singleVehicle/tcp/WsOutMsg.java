package cn.bcd.server.simulator.singleVehicle.tcp;

/**
 * @param flag    1、连接tcp网关
 *                2、更新运行数据结果
 *                101、同步服务器运行数据到客户端
 *                102、发送数据到网关成功通知
 *                103、接收到网关的响应数据
 *                104、tcp网关断开通知
 *                999、车辆使用中
 * @param data
 * @param succeed
 */
public record WsOutMsg(int flag, String data, boolean succeed) {
}
