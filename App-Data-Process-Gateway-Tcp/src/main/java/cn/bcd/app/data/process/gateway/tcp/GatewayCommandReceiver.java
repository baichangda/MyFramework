package cn.bcd.app.data.process.gateway.tcp;

import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import cn.bcd.lib.vehicle.command.CommandReceiver;
import cn.bcd.lib.vehicle.command.Request;
import cn.bcd.lib.vehicle.command.ResponseStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.netty.buffer.Unpooled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
public class GatewayCommandReceiver implements CommandReceiver {

    static final Cache<String, Request<?, ?>> cache = Caffeine.newBuilder().<String, Request<?, ?>>expireAfter(new Expiry<>() {
        @Override
        public long expireAfterCreate(String key, Request<?, ?> value, long currentTime) {
            return TimeUnit.SECONDS.toNanos(value.timeout);
        }

        @Override
        public long expireAfterUpdate(String key, Request<?, ?> value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(String key, Request<?, ?> value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }).build();

    @Override
    public void onRequest(Request<?, ?> request) {
        //放入缓存
        if (request.waitVehicleResponse) {
            cache.put(request.id, request);
        }
        Session session = Session.getSession(request.vin);
        if (session == null) {
            return;
        }
        //写报文到车端
        session.channel.writeAndFlush(Unpooled.wrappedBuffer(request.toPacketBytes()));
        //判断直接响应
        if (!request.waitVehicleResponse) {
            CommandReceiver.response(request, ResponseStatus.success, null);
        }
    }

    public void onResponse(String vin, PacketFlag flag, byte[] bytes) {
        String id = Request.toId(vin, flag);
        Request<?, ?> request = cache.getIfPresent(id);
        if (request == null) {
            return;
        }
        //清除缓存
        cache.invalidate(id);
        //响应
        CommandReceiver.response(request, ResponseStatus.success, bytes);
    }
}
