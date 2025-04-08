package cn.bcd.server.data.process.gateway.tcp;

import cn.bcd.lib.parser.protocol.gb32960.data.PacketFlag;
import cn.bcd.lib.vehicle.command.CommandProp;
import cn.bcd.lib.vehicle.command.CommandReceiver;
import cn.bcd.lib.vehicle.command.Request;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.netty.buffer.Unpooled;
import org.checkerframework.checker.index.qual.NonNegative;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
public class GatewayCommandReceiver implements CommandReceiver {


    @Autowired
    CommandProp commandProp;

    @Autowired
    KafkaTemplate<String, byte[]> kafkaTemplate;

    static final Cache<String, Request<?, ?>> cache = Caffeine.newBuilder().<String, Request<?, ?>>expireAfter(new Expiry<>() {
        @Override
        public long expireAfterCreate(String key, Request<?, ?> value, long currentTime) {
            return TimeUnit.SECONDS.toNanos(value.timeout);
        }

        @Override
        public long expireAfterUpdate(String key, Request<?, ?> value, long currentTime, @NonNegative long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(String key, Request<?, ?> value, long currentTime, @NonNegative long currentDuration) {
            return currentDuration;
        }
    }).build();

    @Override
    public void onRequest(Request<?, ?> request) {
        //放入缓存
        cache.put(request.id, request);
        Session session = Session.getSession(request.vin);
        if (session == null) {
            return;
        }
        //写报文到车端
        session.channel.writeAndFlush(Unpooled.wrappedBuffer(request.toPacketBytes()));
    }

    public void onResponse(String vin, PacketFlag flag, byte[] bytes) {
        Request<?, ?> request = cache.getIfPresent(Request.toId(vin, flag));
        if (request != null) {
            kafkaTemplate.send(commandProp.responseTopic, request.id, bytes);
        }
    }
}
