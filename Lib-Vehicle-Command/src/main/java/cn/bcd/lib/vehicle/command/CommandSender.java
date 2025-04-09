package cn.bcd.lib.vehicle.command;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.base.kafka.KafkaUtil;
import cn.bcd.lib.base.util.DateZoneUtil;
import cn.bcd.lib.base.util.HexUtil;
import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@EnableConfigurationProperties(CommandProp.class)
@ConditionalOnProperty("lib.vehicle.command.sender")
@Component
public class CommandSender {

    static Logger logger = LoggerFactory.getLogger(CommandSender.class);

    static Map<String, Request<?, ?>> requestMap = new HashMap<>();

    static RedisTemplate<String, String> redisTemplate;

    static KafkaProducer<String, byte[]> kafkaProducer;

    static ScheduledExecutorService workExecutor = Executors.newSingleThreadScheduledExecutor();

    static CommandProp commandProp;

    public CommandSender(RedisTemplate<String, String> redisTemplate,
                         CommandProp commandProp,
                         KafkaProperties kafkaProp) {
        CommandSender.redisTemplate = redisTemplate;
        CommandSender.commandProp = commandProp;
        CommandSender.kafkaProducer = KafkaUtil.newKafkaProducer_string_bytes(kafkaProp.getProducer().buildProperties(new DefaultSslBundleRegistry()));
    }


    public static final String redis_key_prefix_command_lock = "commandLock:";

    /**
     * 判断车辆是否在线
     * 即车辆最后一条运行报文上报时间是否在{@link Const#vehicle_offline_max_time_second}之内
     *
     * @param vin
     * @return
     */
    public static boolean online(String vin) {
        String s = redisTemplate.opsForValue().get(Const.redis_key_prefix_vehicle_last_packet_time + vin);
        if (s == null) {
            return false;
        } else {
            long l = Long.parseLong(s);
            return (System.currentTimeMillis() - l) < Const.vehicle_offline_max_time_second * 1000;
        }
    }

    public static boolean tryLock(String vin, PacketFlag flag, int timeout) {
        String key = redis_key_prefix_command_lock + vin + "," + HexUtil.hexDump((byte) flag.type);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, DateZoneUtil.dateToString_second(new Date()), timeout * 2L, TimeUnit.SECONDS));
    }

    public static void releaseLock(String vin, PacketFlag flag) {
        String key = redis_key_prefix_command_lock + vin + "," + HexUtil.hexDump((byte) flag.type);
        redisTemplate.delete(key);
    }

    /**
     * 发送下行命令
     *
     * @param vin                 vin
     * @param command             命令数据结构
     * @param timeout             发送超时时间(s)
     * @param callback            接收到响应回调
     * @param waitVehicleResponse 在发送命令到车端后是否等待车端响应
     *                            如果为true，则发送命令后，会等待车端响应，并将车端响应结果设置到{@link Response#content}中
     *                            否则在发送命令后直接返回成功、且{@link Response#content}为null
     * @param <T>                 响应回调的数据类型
     */
    public static <T, R> void send(String vin,
                                   Command<T, R> command,
                                   int timeout,
                                   CommandCallback<T, R> callback,
                                   boolean waitVehicleResponse) {
        boolean online = online(vin);
        PacketFlag flag = command.flag;
        if (!online) {
            callback.callback(new Response<>(vin, flag, ResponseStatus.offline, null));
            return;
        }
        if (!tryLock(vin, flag, timeout)) {
            callback.callback(new Response<>(vin, flag, ResponseStatus.busy, null));
            return;
        }
        Request<T, R> request = new Request<>();
        request.setId(Request.toId(vin, flag));
        request.setFlag(flag);
        request.setContent(command.toRequestBytes());
        request.setVin(vin);
        request.setCommand(command);
        request.setWaitVehicleResponse(waitVehicleResponse);
        request.setTimeout(timeout);
        request.setCallback(callback);
        request.setTimeoutFuture(
                workExecutor.schedule(() -> {
                    Request<?, ?> remove = requestMap.remove(request.getId());
                    if (remove != null) {
                        try {
                            remove.callback.callback(new Response<>(remove.getVin(), remove.flag, ResponseStatus.timeout, null));
                        } catch (Exception ex) {
                            logger.error("error", ex);
                        } finally {
                            releaseLock(remove.vin, remove.flag);
                        }
                    }
                }, timeout, TimeUnit.SECONDS)
        );
        workExecutor.execute(() -> {
            requestMap.put(request.getId(), request);
            try {
                ProducerRecord<String, byte[]> record = new ProducerRecord<>(commandProp.requestTopic, vin, JsonUtil.toJsonAsBytes(request));
                kafkaProducer.send(record);
            } catch (Exception ex) {
                logger.error("error", ex);
            }
        });
    }
}
