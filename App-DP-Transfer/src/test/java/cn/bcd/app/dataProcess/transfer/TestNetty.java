package cn.bcd.app.dataProcess.transfer;

import cn.bcd.lib.base.util.DateZoneUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import java.nio.channels.spi.SelectorProvider;
import java.util.Date;

public class TestNetty {
    @Test
    public void testClient() throws InterruptedException {
        try (MultiThreadIoEventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory(SelectorProvider.provider()))) {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup);
            bootstrap.handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter(){
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            ByteBuf byteBuf = (ByteBuf) msg;
                            byte[] bytes=new byte[byteBuf.readableBytes()];
                            byteBuf.readBytes(bytes);
                            System.out.println(new String(bytes));
                            String s = DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date());
                            ctx.writeAndFlush(Unpooled.wrappedBuffer(s.getBytes()));
                        }

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            super.channelActive(ctx);
                        }
                    });
                }
            });
            bootstrap.channel(NioSocketChannel.class).option(ChannelOption.TCP_NODELAY, true);
            ChannelFuture channelFuture = bootstrap.connect("127.0.0.1", 8888).sync();
            channelFuture.addListener(f->{
                System.out.println("connect succeed");
            });
            channelFuture.channel().closeFuture().await();
        }
    }
}
