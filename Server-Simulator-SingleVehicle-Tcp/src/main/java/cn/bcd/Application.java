package cn.bcd;

import cn.bcd.server.simulator.singleVehicle.tcp.CommandLineStarter;
import picocli.CommandLine;

public class Application {
    public static void main(String[] args) throws Exception {
        new CommandLine(new CommandLineStarter()).execute(args);
    }
}