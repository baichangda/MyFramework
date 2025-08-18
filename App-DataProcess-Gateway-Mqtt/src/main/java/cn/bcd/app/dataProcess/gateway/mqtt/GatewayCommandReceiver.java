package cn.bcd.app.dataProcess.gateway.mqtt;

import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.util.HexUtil;
import cn.bcd.lib.vehicle.command.CommandReceiver;
import cn.bcd.lib.vehicle.command.Request;
import cn.bcd.lib.vehicle.command.ResponseStatus;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class GatewayCommandReceiver implements CommandReceiver {

    static Logger logger = LoggerFactory.getLogger(GatewayCommandReceiver.class);

    @Autowired
    Mqtt5AsyncClient client;

    @Autowired
    GatewayProp gatewayProp;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    public static String redisKeyPre_commandRequest = "commandRequest:";


    public static String getRedisKey(String vin, byte flag) {
        return GatewayCommandReceiver.redisKeyPre_commandRequest + vin + "," + HexUtil.hexDump(flag);
    }

    @Override
    public void onRequest(Request<?, ?> request) {
        String vin = request.getVin();
        String redisKey = getRedisKey(vin, (byte) request.flag);
        try {
            //存储请求到redis中
            if (request.waitVehicleResponse) {
                redisTemplate.opsForValue().set(redisKey, JsonUtil.toJson(request), Duration.ofSeconds(request.timeout * 2L));
            }
            //写报文到车端
            byte[] packetBytes = request.toPacketBytes();
            client.publish(Mqtt5Publish.builder().topic(gatewayProp.getResponseTopicPrefix() + vin).payload(packetBytes).build());
            logger.info("GatewayCommandReceiver --> command request send to MQTT, Request flag：{}, message：{}", request.flag, ByteBufUtil.hexDump(packetBytes));
            //判断直接响应
            if (!request.waitVehicleResponse) {
                CommandReceiver.response(request, ResponseStatus.success, null);
            }
        } catch (Exception e) {
            logger.error("GatewayCommandReceiver --> command request error", e);
            CommandReceiver.response(request, ResponseStatus.program_error, null);
            //删除redis中的数据
            redisTemplate.delete(redisKey);
        }
    }

    public void onResponse(String vin, int flag, byte[] bytes) {
        String redisKey = getRedisKey(vin, (byte) flag);
        String val = redisTemplate.opsForValue().get(redisKey);
        if (val == null) {
            return;
        }
        try {
            Request<?, ?> request = JsonUtil.OBJECT_MAPPER.readValue(val, Request.class);
            //响应
            CommandReceiver.response(request, ResponseStatus.success, bytes);
        } catch (Exception ex) {
            logger.error("error", ex);
        } finally {
            //清除缓存
            redisTemplate.delete(redisKey);
        }
    }
}
