package cn.bcd.server.simulator.pressTest.tcp;

import cn.bcd.server.simulator.pressTest.tcp.gb32960.PressTest_gb32960;
import picocli.CommandLine;

@CommandLine.Command(name = "pressTest", mixinStandardHelpOptions = true,subcommands = {PressTest_gb32960.class})
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
