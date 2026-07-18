package cn.bcd.lib.parser.protocol.gb32960.v2025.processor;


import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.gb32960.v2025.data.*;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketDataProcessor implements Processor<PacketData> {
    static Logger logger = LoggerFactory.getLogger(PacketDataProcessor.class);

    final Processor<VehicleLoginData> processor_vehicleLoginData = Parser.getProcessor(VehicleLoginData.class);
    final VehicleRunDataProcessor processor_vehicleRunData = new VehicleRunDataProcessor();
    final Processor<VehicleLogoutData> processor_vehicleLogoutData = Parser.getProcessor(VehicleLogoutData.class);
    final Processor<PlatformLoginData> processor_platformLoginData = Parser.getProcessor(PlatformLoginData.class);
    final Processor<PlatformLogoutData> processor_platformLogoutData = Parser.getProcessor(PlatformLogoutData.class);
    final Processor<TimeData> processor_timeData = Parser.getProcessor(TimeData.class);
    final Processor<ParamQueryRequest> processor_paramQueryRequest = Parser.getProcessor(ParamQueryRequest.class);
    final Processor<ParamQueryResponse> processor_paramQueryResponse = Parser.getProcessor(ParamQueryResponse.class);
    final Processor<ParamSetRequest> processor_paramSetRequest = Parser.getProcessor(ParamSetRequest.class);
    final Processor<ActivateRequest> processor_activateRequest = Parser.getProcessor(ActivateRequest.class);
    final Processor<ActivateResponse> processor_activateResponse = Parser.getProcessor(ActivateResponse.class);
    final Processor<DataEncryptKeyExchange> processor_dataEncryptKeyExchange = Parser.getProcessor(DataEncryptKeyExchange.class);

    @Override
    public PacketData process(ByteBuf data, ProcessContext processContext) {
        Packet packet = (Packet) processContext.instance;
        boolean cmd = packet.replyFlag == 0xfe;
        PacketFlag flag = packet.flag;
        switch (flag) {
            case vehicle_login_data -> {
                if (cmd) {
                    return processor_vehicleLoginData.process(data, processContext);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case vehicle_run_data, vehicle_supplement_data -> {
                if (cmd) {
                    return processor_vehicleRunData.process(data, processContext);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case vehicle_logout_data -> {
                if (cmd) {
                    return processor_vehicleLogoutData.process(data, processContext);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case platform_login_data -> {
                if (cmd) {
                    return processor_platformLoginData.process(data, processContext);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case platform_logout_data -> {
                if (cmd) {
                    return processor_platformLogoutData.process(data, processContext);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case activate -> {
                if (cmd) {
                    return processor_activateRequest.process(data, processContext);
                } else {
                    return processor_activateResponse.process(data, processContext);
                }
            }
            case data_encrypt_key_exchange -> {
                if (cmd) {
                    return processor_dataEncryptKeyExchange.process(data, processContext);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case param_query -> {
                if (cmd) {
                    return processor_paramQueryRequest.process(data, processContext);
                } else {
                    return processor_paramQueryResponse.process(data, processContext);
                }
            }
            case param_set -> {
                if (cmd) {
                    return processor_paramSetRequest.process(data, processContext);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case terminal_control_command -> {
                if (cmd) {
                    return TerminalControlCommand.read(packet.contentLength, data);
                } else {
                    return processor_timeData.process(data, processContext);
                }
            }
            case heartbeat, terminal_time_validate -> {
                return processor_timeData.process(data, processContext);
            }
            default -> {
                throw BaseException.get("flag[{}] not support", flag);
            }
        }
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext processContext, PacketData instance) {
        Packet packet = (Packet) processContext.instance;
        PacketFlag flag = packet.flag;
        boolean cmd = packet.replyFlag == 0xfe;
        switch (flag) {
            case vehicle_login_data -> {
                if (cmd) {
                    processor_vehicleLoginData.deProcess(data, processContext, (VehicleLoginData) instance);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case vehicle_run_data, vehicle_supplement_data -> {
                if (cmd) {
                    processor_vehicleRunData.deProcess(data, processContext, (VehicleRunData) instance);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case vehicle_logout_data -> {
                if (cmd) {
                    processor_vehicleLogoutData.deProcess(data, processContext, (VehicleLogoutData) instance);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case platform_login_data -> {
                if (cmd) {
                    processor_platformLoginData.deProcess(data, processContext, (PlatformLoginData) instance);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case platform_logout_data -> {
                if (cmd) {
                    processor_platformLogoutData.deProcess(data, processContext, (PlatformLogoutData) instance);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case activate -> {
                if (cmd) {
                    processor_activateRequest.deProcess(data, processContext, (ActivateRequest) instance);
                } else {
                    processor_activateResponse.deProcess(data, processContext, (ActivateResponse) instance);
                }
            }
            case data_encrypt_key_exchange -> {
                if (cmd) {
                    processor_dataEncryptKeyExchange.deProcess(data, processContext, (DataEncryptKeyExchange) instance);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case param_query -> {
                if (cmd) {
                    processor_paramQueryRequest.deProcess(data, processContext, (ParamQueryRequest) instance);
                } else {
                    processor_paramQueryResponse.deProcess(data, processContext, (ParamQueryResponse) instance);
                }
            }
            case param_set -> {
                if (cmd) {
                    processor_paramSetRequest.deProcess(data, processContext, (ParamSetRequest) instance);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case terminal_control_command -> {
                if (cmd) {
                    ((TerminalControlCommand) instance).write(data);
                } else {
                    processor_timeData.deProcess(data, processContext, (TimeData) instance);
                }
            }
            case heartbeat, terminal_time_validate -> {
                processor_timeData.deProcess(data, processContext, (TimeData) instance);
            }
            default -> {
                throw BaseException.get("flag[{}] not support", flag);
            }
        }
    }
}
