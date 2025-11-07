package cn.bcd.lib.spring.vehicle.command;

public enum ResponseStatus {
    /**
     * 成功有两种情况
     * {@link Request#waitVehicleResponse}表示不同的成功场景
     * true - 此时表示成功接收到了响应报文、具体的响应码参考replyFlag
     * false - 此时请求不会考虑响应、表示请求发送成功
     */
    success(0, "成功"),
    /**
     * 车辆不在线
     * 指令未发送
     */
    offline(1, "车辆不在线"),
    /**
     * 指令已发送
     * 等待响应超时
     * 只会出现在{@link Request#waitVehicleResponse}为true场景
     */
    timeout(2, "超时"),
    /**
     * 有相同的指令正在进行中
     * 指令未发送
     */
    busy(3, "指令正在发送"),

    /**
     * 程序出错
     */
    program_error(4, "程序出错");

    public final int code;

    public final String message;

    ResponseStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ResponseStatus from(int code) {
        for (ResponseStatus status : ResponseStatus.values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
