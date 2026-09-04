package cn.bcd.app.dataProcess.transfer;

import cn.bcd.lib.base.util.DateZoneUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class TestNio {
    @Test
    public void testClient() throws InterruptedException, IOException {
        try (SocketChannel socketChannel = SocketChannel.open();
             Selector selector = Selector.open()) {
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress("127.0.0.1", 8888));
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            //监听连接事件
            while (true) {
                int select = selector.select(1000L);
                if (select > 0) {
                    break;
                }
            }
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            for (SelectionKey selectionKey : selector.selectedKeys()) {
                if (selectionKey.isConnectable()) {
                    if (socketChannel.finishConnect()) {
                        System.out.println("connect succeed");
                        break;
                    }else{
                        System.out.println("connect failed");
                    }
                }
            }

            socketChannel.register(selector, SelectionKey.OP_READ);
            //监听连接事件
            while (true) {
                int select = selector.select(1000L);
                if (select > 0) {
                    break;
                }
            }
            for (SelectionKey selectionKey : selector.selectedKeys()) {
                if (selectionKey.isReadable()) {
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    int readLen = socketChannel.read(buffer);
                    if (readLen > 0) {
                        // 切换为读模式
                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        String message = new String(data);
                        System.out.println("receive:\n" + message);
                        String s = DateZoneUtil.dateToStr_yyyyMMddHHmmss(new Date());
                        socketChannel.write(ByteBuffer.wrap(s.getBytes()));
                    }
                }
            }
        }
    }
}
