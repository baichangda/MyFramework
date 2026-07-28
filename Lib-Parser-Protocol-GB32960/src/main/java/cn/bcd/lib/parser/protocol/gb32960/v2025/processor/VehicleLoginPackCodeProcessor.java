package cn.bcd.lib.parser.protocol.gb32960.v2025.processor;

import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.VehicleLoginData;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class VehicleLoginPackCodeProcessor implements Processor<String> {

    static Logger logger = LoggerFactory.getLogger(VehicleLoginPackCodeProcessor.class);

    @Override
    public String process(ByteBuf data, ProcessContext processContext) {
        VehicleLoginData vehicleLoginData = (VehicleLoginData) processContext.instance;
        byte[] packNums = vehicleLoginData.packNums;
        int sum = 0;
        for (byte packNum : packNums) {
            sum += packNum & 0xFF;
        }
        return data.readCharSequence(sum * 24, StandardCharsets.UTF_8).toString();
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext processContext, String instance) {
        data.writeCharSequence(instance, StandardCharsets.UTF_8);
    }
}
