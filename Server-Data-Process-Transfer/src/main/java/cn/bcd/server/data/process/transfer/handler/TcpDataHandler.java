package cn.bcd.server.data.process.transfer.handler;


public interface TcpDataHandler {
    void handle(String vin, byte[] bytes, Context context) throws Exception;
}
