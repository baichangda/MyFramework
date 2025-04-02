package cn.bcd.lib.vehicle.command;

public enum ResponseStatus {
    SUCCESS(0, "成功"),
    FAIL(1, "失败"),
    TIMEOUT(2, "超时"),
    OFFLINE(3, "车辆不在线"),
    BUSY(4, "指令正在发送");

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
