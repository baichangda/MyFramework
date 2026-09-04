package cn.bcd.app.dataProcess.transfer.v2016.handler;

public interface KafkaDataHandler {
    default void init(String vin, Context context)  throws Exception {
    }

    default void destroy(String vin, Context context)  throws Exception {
    }

    void handle(String vin, byte[] bytes, Context context) throws Exception;
}
