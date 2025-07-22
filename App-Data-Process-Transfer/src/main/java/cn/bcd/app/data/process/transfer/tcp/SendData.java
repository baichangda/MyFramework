package cn.bcd.app.data.process.transfer.tcp;

import cn.bcd.lib.base.executor.SingleThreadExecutor;

public record SendData(byte[] data, Runnable sendCallback, SingleThreadExecutor executor) {
    public void callback(){
        executor.execute(sendCallback);
    }
}
