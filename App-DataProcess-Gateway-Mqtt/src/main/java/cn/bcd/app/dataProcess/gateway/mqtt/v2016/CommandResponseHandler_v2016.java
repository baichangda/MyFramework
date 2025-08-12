package cn.bcd.app.dataProcess.gateway.mqtt.v2016;

import cn.bcd.app.dataProcess.gateway.mqtt.GatewayCommandReceiver;
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.PacketFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class CommandResponseHandler_v2016 implements DataHandler_v2016 {

    static Logger logger = LoggerFactory.getLogger(CommandResponseHandler_v2016.class);

    @Autowired
    GatewayCommandReceiver gatewayCommandReceiver;

    @Override
    public void handle(String vin, PacketFlag flag, byte[] data, Context_v2016 context) {
        byte[] content = new byte[data.length - 25];
        System.arraycopy(data, 24, content, 0, content.length);
        gatewayCommandReceiver.onResponse(vin, flag.type, content);
    }
}
