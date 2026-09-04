package cn.bcd.app.dp.transfer.v2016.tcp;


public interface TcpDataHandler {
    void handle(String vin, byte[] bytes) throws Exception;
}
