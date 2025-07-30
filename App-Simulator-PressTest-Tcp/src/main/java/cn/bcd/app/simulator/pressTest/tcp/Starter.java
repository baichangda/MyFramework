package cn.bcd.app.simulator.pressTest.tcp;

import cn.bcd.app.simulator.pressTest.tcp.v2016.Starter_v2016;
import picocli.CommandLine;

@CommandLine.Command(name = "pressTest", mixinStandardHelpOptions = true,subcommands = {Starter_v2016.class})
public class Starter {
    @CommandLine.Option(names = {"-p", "--period"}, description = "report period", required = false, defaultValue = "10", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    int period;
    @CommandLine.Option(names = {"-s", "--startIndex"}, description = "vehicle start index", required = true)
    int startIndex;
    @CommandLine.Option(names = {"-n", "--num"}, description = "vehicle num", required = true)
    int num;
    @CommandLine.Option(names = {"-t", "--tcpServerAddress"}, description = "tcp server address", required = true)
    String tcpServerAddress;
}
