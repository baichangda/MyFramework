package cn.bcd;

import cn.bcd.simulator.singleVehicle.tcp.HttpServer;
import picocli.CommandLine;

public class Application {
    public static void main(String[] args) throws Exception {
        new CommandLine(new HttpServer()).execute(args);
    }
}