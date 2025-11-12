package cn.bcd.app;

import cn.bcd.app.transponder.gb32960.Starter;
import picocli.CommandLine;

public class Application {
    public static void main(String[] args) throws Exception {
        new CommandLine(new Starter()).execute(args);
    }
}