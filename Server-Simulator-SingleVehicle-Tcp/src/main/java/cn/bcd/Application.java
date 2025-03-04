package cn.bcd;

import cn.bcd.server.simulator.singleVehicle.tcp.HttpServerCommandLine;
import picocli.CommandLine;

public class Application {
    public static void main(String[] args) throws Exception {
        new CommandLine(new HttpServerCommandLine()).execute(args);
    }
}