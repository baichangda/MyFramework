package cn.bcd;

import cn.bcd.server.simulator.pressTest.tcp.Starter;
import picocli.CommandLine;

public class Application {
    public static void main(String[] args) throws Exception {
        new CommandLine(new Starter()).execute(args);
    }
}