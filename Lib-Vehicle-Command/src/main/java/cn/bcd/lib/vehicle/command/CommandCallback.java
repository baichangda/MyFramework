package cn.bcd.lib.vehicle.command;

public interface CommandCallback<T,R> {

    void callback(Response<T, R> response);
}
