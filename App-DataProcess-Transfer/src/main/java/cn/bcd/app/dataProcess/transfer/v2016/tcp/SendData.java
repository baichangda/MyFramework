package cn.bcd.app.dataProcess.transfer.v2016.tcp;

import io.netty.util.concurrent.EventExecutor;

public record SendData(byte[] data, Runnable sendCallback, EventExecutor executor) {
    public void callback(){
        executor.execute(sendCallback);
    }
}
