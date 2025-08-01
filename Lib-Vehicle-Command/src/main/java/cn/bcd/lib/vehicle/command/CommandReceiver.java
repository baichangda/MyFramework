package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.base.json.JsonUtil;
import io.netty.buffer.ByteBufUtil;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CommandReceiver {

    Logger logger = LoggerFactory.getLogger(CommandReceiver.class);

    void onRequest(Request<?, ?> request);

    static void response(Request<?, ?> request, ResponseStatus status, byte[] packetBytes) {
        logger.info("response command vin[{}] flag[{}] requestId[{}] status[{}] packetBytes[{}]", request.vin, request.flag, request.id, status, packetBytes == null ? null : ByteBufUtil.hexDump(packetBytes));
        Response<?, ?> response = new Response<>(request.getVin(), request.getFlag(), status, request.getVersion(), packetBytes);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(CommandRequestConsumer.commandProp.responseTopic, request.id, JsonUtil.toJsonAsBytes(response));
        CommandRequestConsumer.kafkaProducer.send(record);
    }
}
