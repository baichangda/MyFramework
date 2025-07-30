package cn.bcd.app.simulator.singleVehicle.tcp.v2025;

import cn.bcd.app.simulator.singleVehicle.tcp.HttpServer;
import picocli.CommandLine;

@CommandLine.Command(name = "v2025", mixinStandardHelpOptions = true)
public class Starter_v2025 extends HttpServer {
    public Starter_v2025() {
        super(VehicleData_v2025::new);
    }
}
