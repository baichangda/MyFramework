package cn.bcd.app.simulator.singleVehicle.tcp;

import cn.bcd.app.simulator.singleVehicle.tcp.v2016.Starter_v2016;
import cn.bcd.app.simulator.singleVehicle.tcp.v2025.Starter_v2025;
import picocli.CommandLine;

@CommandLine.Command(name = "singleVehicle", mixinStandardHelpOptions = true,
        subcommands = {Starter_v2016.class, Starter_v2025.class}
)
public class Starter {
    @CommandLine.Option(names = {"-p", "--httpServerPort"}, description = "http server port", required = false, defaultValue = "45678", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    int httpServerPort;
    @CommandLine.Option(names = {"-s", "--sendPeriod"}, description = "send period in second", required = false, defaultValue = "10", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    int sendPeriod;
}
