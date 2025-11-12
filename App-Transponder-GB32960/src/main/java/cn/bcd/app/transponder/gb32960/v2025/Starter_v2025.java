package cn.bcd.app.transponder.gb32960.v2025;

import cn.bcd.app.transponder.gb32960.TcpServer;
import io.netty.channel.Channel;
import picocli.CommandLine;

@CommandLine.Command(name = "v2025", mixinStandardHelpOptions = true)
public class Starter_v2025 extends TcpServer {
    @Override
    protected void init(Channel ch) {
        ch.pipeline().addLast(new DataInboundHandler_v2025());
    }
}
