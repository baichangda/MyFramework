package cn.bcd.app.simulator.singleVehicle.tcp.gb32960.v2016;

import cn.bcd.app.simulator.singleVehicle.tcp.gb32960.HttpServer;
import picocli.CommandLine;

@CommandLine.Command(name = "v2016", mixinStandardHelpOptions = true)
public class Starter_v2016 extends HttpServer {
    public Starter_v2016() {
        super(VehicleData_v2016::new);
    }
}
