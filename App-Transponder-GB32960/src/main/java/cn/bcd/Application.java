package cn.bcd;

import cn.bcd.app.transponder.gb32960.Starter;
import picocli.CommandLine;

public class Application {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Starter()).execute(args);
        System.exit(exitCode);
    }
}
