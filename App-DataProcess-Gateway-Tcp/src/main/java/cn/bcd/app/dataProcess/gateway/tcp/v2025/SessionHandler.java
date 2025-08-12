package cn.bcd.app.dataProcess.gateway.tcp.v2025;

import cn.bcd.app.dataProcess.gateway.tcp.Session;
import cn.bcd.app.dataProcess.gateway.tcp.SessionClusterManager;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.PacketFlag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;


@Order(10)
@Component
public class SessionHandler implements DataHandler_v2025{

    @Autowired
    SessionClusterManager sessionClusterManager;

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2025 context) throws Exception {
        if (context.session == null) {
            //构造会话
            context.session = new Session(vin, context.ctx.channel());
            //发送会话通知到其他集群、踢掉无用的session
            sessionClusterManager.send(context.session);
        }
    }
}
