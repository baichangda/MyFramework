package cn.bcd.app.dataProcess.transfer.v2016.tcp;

import cn.bcd.lib.base.executor.SingleThreadExecutor;

public record SendData(byte[] data, Runnable sendCallback, SingleThreadExecutor executor) {
    public void callback(){
        executor.execute(sendCallback);
    }
}
