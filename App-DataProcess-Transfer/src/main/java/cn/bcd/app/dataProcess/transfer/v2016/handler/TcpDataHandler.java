package cn.bcd.app.dataProcess.transfer.v2016.handler;


public interface TcpDataHandler {
    void handle(String vin, byte[] bytes, Context context) throws Exception;
}
