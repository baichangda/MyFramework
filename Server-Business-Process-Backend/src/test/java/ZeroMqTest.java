import cn.bcd.lib.base.util.DateZoneUtil;
import org.junit.jupiter.api.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class ZeroMqTest {
    @Test
    public void test() {

        Executors.newSingleThreadExecutor().execute(()->{
            try (ZContext context = new ZContext()) {
                ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                socket.connect("tcp://*:5555");
                while (!Thread.currentThread().isInterrupted()) {
                    socket.send(DateZoneUtil.dateToString_second(new Date()));
                    byte[] recv = socket.recv(0);
                    String s = new String(recv);
                    System.out.println(s);
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5555");

            while (!Thread.currentThread().isInterrupted()) {
                byte[] recv = socket.recv(0);
                String s = new String(recv);
                System.out.println(s);
                socket.send((s + ",hello").getBytes(), 0);
            }
        }


    }

}
