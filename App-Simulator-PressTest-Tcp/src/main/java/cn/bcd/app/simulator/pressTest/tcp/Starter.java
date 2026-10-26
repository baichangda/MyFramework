package cn.bcd.app.simulator.pressTest.tcp;

import cn.bcd.app.simulator.pressTest.tcp.v2016.Starter_v2016;
import cn.bcd.app.simulator.pressTest.tcp.v2025.Starter_v2025;
import com.google.common.net.HostAndPort;
import picocli.CommandLine;

@CommandLine.Command(name = "pressTest", mixinStandardHelpOptions = true
        , subcommands = {Starter_v2016.class, Starter_v2025.class})
public class Starter {
    @CommandLine.Option(names = {"-p", "--period"}, description = "report period", required = false, defaultValue = "10", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    int period;
    @CommandLine.Option(names = {"-s", "--startIndex"}, description = "vehicle start index", required = true)
    int startIndex;
    @CommandLine.Option(names = {"-n", "--num"}, description = "vehicle num", required = true)
    int num;
    @CommandLine.Option(names = {"-t", "--tcpServerAddress"}, description = "tcp server address", required = true)
    String tcpServerAddress;

    HostAndPort validate() {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be greater than 0");
        }
        if (num <= 0) {
            throw new IllegalArgumentException("num must be greater than 0");
        }
        if (startIndex < 0 || (long) startIndex + num > 10_000_000L) {
            throw new IllegalArgumentException("vehicle indexes must be between 0 and 9999999");
        }
        if (tcpServerAddress == null) {
            throw new IllegalArgumentException("tcpServerAddress is required");
        }
        HostAndPort address = HostAndPort.fromString(tcpServerAddress).requireBracketsForIPv6();
        if (address.getHost().isBlank() || !address.hasPort() || address.getPort() == 0) {
            throw new IllegalArgumentException("tcpServerAddress must contain a host and a port between 1 and 65535");
        }
        return address;
    }
}
