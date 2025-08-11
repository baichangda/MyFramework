package cn.bcd.app.dataProcess.transfer.handler;


public interface TcpDataHandler {
    void handle(String vin, byte[] bytes, Context context) throws Exception;
}
