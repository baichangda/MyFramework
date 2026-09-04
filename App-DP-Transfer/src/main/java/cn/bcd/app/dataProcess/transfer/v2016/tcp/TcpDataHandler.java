package cn.bcd.app.dataProcess.transfer.v2016.tcp;


public interface TcpDataHandler {
    void handle(String vin, byte[] bytes) throws Exception;
}
