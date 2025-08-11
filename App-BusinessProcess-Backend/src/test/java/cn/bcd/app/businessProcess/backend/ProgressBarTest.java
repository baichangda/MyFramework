package cn.bcd.app.businessProcess.backend;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.junit.jupiter.api.Test;

public class ProgressBarTest {
    @Test
    public void test(){
        try(ProgressBar progressBar = new ProgressBarBuilder()
                .setTaskName("test")
                .setInitialMax(1000)
                .showSpeed()
                .setStyle(ProgressBarStyle.UNICODE_BLOCK)
                .setUpdateIntervalMillis(500)
                .build()) {
            while (true){
                progressBar.stepBy(1);
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
