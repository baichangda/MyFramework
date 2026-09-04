package cn.bcd.app.dp.transfer.v2016.tcp;

import io.netty.util.concurrent.EventExecutor;

public record SendData(byte[] data, Runnable sendCallback, EventExecutor executor) {
    public void callback(){
        executor.execute(sendCallback);
    }
}
