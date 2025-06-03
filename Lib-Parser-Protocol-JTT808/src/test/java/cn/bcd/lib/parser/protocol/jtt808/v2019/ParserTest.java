package cn.bcd.lib.parser.protocol.jtt808.v2019;

import cn.bcd.lib.parser.base.Parser;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserTest {
    static Logger logger = LoggerFactory.getLogger(ParserTest.class);

    @Test
    public void sample() {
        Parser.withDefaultLogCollector_parse();
        Parser.withDefaultLogCollector_deParse();
        Parser.enableGenerateClassFile();
        Parser.enablePrintBuildLog();
    }
}
