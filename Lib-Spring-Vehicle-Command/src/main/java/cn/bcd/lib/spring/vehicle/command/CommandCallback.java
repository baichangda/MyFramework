package cn.bcd.lib.spring.vehicle.command;

public interface CommandCallback<T,R> {

    void callback(Response<T, R> response);
}
