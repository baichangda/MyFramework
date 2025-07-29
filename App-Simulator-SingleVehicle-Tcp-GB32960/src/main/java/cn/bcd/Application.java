package cn.bcd;

import cn.bcd.app.simulator.singleVehicle.tcp.gb32960.Starter;
import picocli.CommandLine;

public class Application {
    public static void main(String[] args) throws Exception {
        new CommandLine(new Starter()).execute(args);
    }
}