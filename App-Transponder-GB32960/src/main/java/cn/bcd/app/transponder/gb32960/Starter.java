package cn.bcd.app.transponder.gb32960;

import cn.bcd.app.transponder.gb32960.v2016.Starter_v2016;
import cn.bcd.app.transponder.gb32960.v2025.Starter_v2025;
import picocli.CommandLine;

@CommandLine.Command(name = "transponder", mixinStandardHelpOptions = true
        , subcommands = {Starter_v2016.class, Starter_v2025.class})
public class Starter {
    @CommandLine.Option(names = {"-p", "--port"}, description = "tcp listen port", required = false, defaultValue = "55555", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    int port;
}
