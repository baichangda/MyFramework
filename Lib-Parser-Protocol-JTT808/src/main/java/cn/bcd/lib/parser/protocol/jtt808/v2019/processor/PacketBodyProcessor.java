package cn.bcd.lib.parser.protocol.jtt808.v2019.processor;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.parser.base.Parser;
import cn.bcd.lib.parser.base.processor.ProcessContext;
import cn.bcd.lib.parser.base.processor.Processor;
import cn.bcd.lib.parser.protocol.jtt808.v2019.data.*;
import io.netty.buffer.ByteBuf;
import static cn.bcd.lib.parser.protocol.jtt808.v2019.data.MsgId.*;

public class PacketBodyProcessor implements Processor<PacketBody> {
    Processor<CommonResponse> processor_commonResponse = Parser.getProcessor(CommonResponse.class);
    Processor<QueryServerTimeResponse> processor_queryServerTimeResponse = Parser.getProcessor(QueryServerTimeResponse.class);
    Processor<SubPacketRequest> processor_serverSubPacketRequest = Parser.getProcessor(SubPacketRequest.class);
    Processor<TerminalAuthentication> processor_terminalAuthentication = Parser.getProcessor(TerminalAuthentication.class);
    Processor<SetTerminalParam> processor_setTerminalParam = Parser.getProcessor(SetTerminalParam.class);
    Processor<QueryTerminalSpecifyParamRequest> processor_queryTerminalSpecifyParamRequest = Parser.getProcessor(QueryTerminalSpecifyParamRequest.class);
    Processor<QueryTerminalParamResponse> processor_queryTerminalParamResponse = Parser.getProcessor(QueryTerminalParamResponse.class);
    Processor<QueryTerminalPropResponse> processor_queryTerminalPropResponse = Parser.getProcessor(QueryTerminalPropResponse.class);
    Processor<IssuedTerminalUpgradeRequest> processor_issuedTerminalUpgradeRequest = Parser.getProcessor(IssuedTerminalUpgradeRequest.class);
    Processor<TerminalUpgradeResResponse> processor_terminalUpgradeResResponse = Parser.getProcessor(TerminalUpgradeResResponse.class);
    Processor<TempPositionFollow> processor_tempPositionFollow = Parser.getProcessor(TempPositionFollow.class);
    Processor<ConfirmAlarmMsg> processor_confirmAlarmMsg = Parser.getProcessor(ConfirmAlarmMsg.class);
    Processor<SetPhoneText> processor_setPhoneText = Parser.getProcessor(SetPhoneText.class);
    Processor<DeleteCircleArea> processor_deleteCircleArea = Parser.getProcessor(DeleteCircleArea.class);
    Processor<DeleteRectangleArea> processor_deleteRectangleArea = Parser.getProcessor(DeleteRectangleArea.class);
    Processor<DeletePolygonArea> processor_deletePolygonArea = Parser.getProcessor(DeletePolygonArea.class);
    Processor<DeletePath> processor_deletePath = Parser.getProcessor(DeletePath.class);
    Processor<QueryAreaOrPathRequest> processor_queryAreaOrPathRequest = Parser.getProcessor(QueryAreaOrPathRequest.class);
    Processor<WaybillReport> processor_waybillReport = Parser.getProcessor(WaybillReport.class);
    Processor<CanDataUpload> processor_canDataUpload = Parser.getProcessor(CanDataUpload.class);
    Processor<MultimediaEventUpload> processor_multiMediaEventUpload = Parser.getProcessor(MultimediaEventUpload.class);
    Processor<CameraTakePhotoCmdRequest> processor_cameraTakePhotoCmdRequest = Parser.getProcessor(CameraTakePhotoCmdRequest.class);
    Processor<CameraTakePhotoCmdResponse> processor_cameraTakePhotoCmdResponse = Parser.getProcessor(CameraTakePhotoCmdResponse.class);
    Processor<StorageMultimediaDataFetchRequest> processor_storageMultiMediaDataFetchRequest = Parser.getProcessor(StorageMultimediaDataFetchRequest.class);
    Processor<StorageMultimediaDataUploadCmd> processor_storageMultiMediaDataUploadCmd = Parser.getProcessor(StorageMultimediaDataUploadCmd.class);
    Processor<RecordingStartCmd> processor_recordingStartCmd = Parser.getProcessor(RecordingStartCmd.class);
    Processor<SingleMultimediaDataFetchUploadCmd> processor_singleMultiMediaDataFetchUploadCmd = Parser.getProcessor(SingleMultimediaDataFetchUploadCmd.class);
    Processor<PlatformRsa> processor_platformRsa = Parser.getProcessor(PlatformRsa.class);
    Processor<TerminalRsa> processor_terminalRsa = Parser.getProcessor(TerminalRsa.class);


    @Override
    public PacketBody process(ByteBuf data, ProcessContext processContext) {
        Packet packet = (Packet) processContext.instance;
        PacketBody packetBody;
        switch (packet.header.msgId) {
            case terminal_common_response, platform_common_response -> {
                packetBody = processor_commonResponse.process(data, processContext);
            }
            case terminal_heartbeat,
                 query_server_time_request,
                 terminal_unregister,
                 query_terminal_param_request,
                 query_terminal_prop_request,
                 query_position_request,
                 link_detection,
                 driver_identity_report_request -> {
                packetBody = null;
            }
            case query_server_time_response -> {
                packetBody = processor_queryServerTimeResponse.process(data, processContext);
            }
            case server_sub_packet_request, terminal_sub_packet_request -> {
                packetBody = processor_serverSubPacketRequest.process(data, processContext);
            }
            case terminal_register_request -> {
                packetBody = TerminalRegisterRequest.read(data, packet.header.msgLen);
            }
            case terminal_register_response -> {
                packetBody = TerminalRegisterResponse.read(data, packet.header.msgLen);
            }
            case terminal_authentication -> {
                packetBody = processor_terminalAuthentication.process(data, processContext);
            }
            case set_terminal_param -> {
                packetBody = processor_setTerminalParam.process(data, processContext);
            }
            case query_terminal_specify_param_request -> {
                packetBody = processor_queryTerminalSpecifyParamRequest.process(data, processContext);
            }
            case query_terminal_param_response -> {
                packetBody = processor_queryTerminalParamResponse.process(data, processContext);
            }
            case terminal_control -> {
                packetBody = TerminalControl.read(data, packet.header.msgLen);
            }
            case query_terminal_prop_response -> {
                packetBody = processor_queryTerminalPropResponse.process(data, processContext);
            }
            case issued_terminal_upgrade_request -> {
                packetBody = processor_issuedTerminalUpgradeRequest.process(data, processContext);
            }
            case terminal_upgrade_res_response -> {
                packetBody = processor_terminalUpgradeResResponse.process(data, processContext);
            }
            case position_data_upload -> {
                packetBody = Position.read(data, packet.header.msgLen);
            }
            case query_position_response -> {
                packetBody = QueryPositionResponse.read(data, packet.header.msgLen);
            }
            case temp_position_follow -> {
                packetBody = processor_tempPositionFollow.process(data, processContext);
            }
            case confirm_alarm_msg -> {
                packetBody = processor_confirmAlarmMsg.process(data, processContext);
            }
            case text_info_issued -> {
                packetBody = TextInfoIssued.read(data, packet.header.msgLen);
            }
            case phone_callback -> {
                packetBody = PhoneCallback.read(data, packet.header.msgLen);
            }
            case set_phone_text -> {
                packetBody = processor_setPhoneText.process(data, processContext);
            }
            case vehicle_control_request -> {
                packetBody = VehicleControlRequest.read(data);
            }
            case vehicle_control_response -> {
                packetBody = VehicleControlResponse.read(data, packet.header.msgLen);
            }
            case set_circle_area -> {
                packetBody = SetCircleArea.read(data);
            }
            case delete_circle_area -> {
                packetBody = processor_deleteCircleArea.process(data, processContext);
            }
            case set_rectangle_area -> {
                packetBody = SetRectangleArea.read(data);
            }
            case delete_rectangle_area -> {
                packetBody = processor_deleteRectangleArea.process(data, processContext);
            }
            case set_polygon_area -> {
                packetBody = SetPolygonArea.read(data);
            }
            case delete_polygon_area -> {
                packetBody = processor_deletePolygonArea.process(data, processContext);
            }
            case set_path -> {
                packetBody = SetPath.read(data);
            }
            case delete_path -> {
                packetBody = processor_deletePath.process(data, processContext);
            }
            case query_area_or_path_request -> {
                packetBody = processor_queryAreaOrPathRequest.process(data, processContext);
            }
            case query_area_or_path_response -> {
                packetBody = QueryAreaOrPathResponse.read(data);
            }
            case driving_recorder_collect_command, driving_recorder_download_command -> {
                packetBody = DrivingRecorderCollectCommand.read(data, packet.header.msgLen);
            }
            case driving_recorder_upload -> {
                packetBody = DrivingRecorderUpload.read(data, packet.header.msgLen);
            }
            case waybill_report -> {
                packetBody = processor_waybillReport.process(data, processContext);
            }
            case driver_identity_report_response -> {
                packetBody = DriverIdentityReportResponse.read(data);
            }
            case position_data_batch_upload -> {
                packetBody = PositionDataBatchUpload.read(data);
            }
            case can_data_upload -> {
                packetBody = processor_canDataUpload.process(data, processContext);
            }
            case multimedia_event_upload -> {
                packetBody = processor_multiMediaEventUpload.process(data, processContext);
            }
            case multimedia_data_upload_request -> {
                packetBody = MultimediaDataUploadRequest.read(data, packet.header.msgLen);
            }
            case multimedia_data_upload_response -> {
                packetBody = MultimediaDataUploadResponse.read(data, packet.header.msgLen);
            }
            case camera_take_photo_cmd_request -> {
                packetBody = processor_cameraTakePhotoCmdRequest.process(data, processContext);
            }
            case camera_take_photo_cmd_response -> {
                packetBody = processor_cameraTakePhotoCmdResponse.process(data, processContext);
            }
            case storage_multimedia_data_fetch_request -> {
                packetBody = processor_storageMultiMediaDataFetchRequest.process(data, processContext);
            }
            case storage_multimedia_data_fetch_response -> {
                packetBody = StorageMultimediaDataFetchResponse.read(data, packet.header.msgLen);
            }
            case storage_multimedia_data_upload_cmd -> {
                packetBody = processor_storageMultiMediaDataUploadCmd.process(data, processContext);
            }
            case recording_start_cmd -> {
                packetBody = processor_recordingStartCmd.process(data, processContext);
            }
            case single_multimedia_data_fetch_upload_cmd -> {
                packetBody = processor_singleMultiMediaDataFetchUploadCmd.process(data, processContext);
            }
            case data_down_stream -> {
                packetBody = DataDownStream.read(data, packet.header.msgLen);
            }
            case data_up_stream -> {
                packetBody = DataUpStream.read(data, packet.header.msgLen);
            }
            case data_compress_report -> {
                packetBody = DataCompressReport.read(data, packet.header.msgLen);
            }
            case platform_rsa -> {
                packetBody = processor_platformRsa.process(data, processContext);
            }
            case terminal_rsa -> {
                packetBody = processor_terminalRsa.process(data, processContext);
            }
            default -> throw BaseException.get("msgId[{}] not support", packet.header.msgId);
        }
        return packetBody;
    }

    @Override
    public void deProcess(ByteBuf data, ProcessContext processContext, PacketBody instance) {
        Packet packet = (Packet) processContext.instance;
        switch (packet.header.msgId) {
            case terminal_common_response, platform_common_response -> {
                processor_commonResponse.deProcess(data, processContext, (CommonResponse) instance);
            }
            case terminal_heartbeat,
                 query_server_time_request,
                 terminal_unregister,
                 query_terminal_param_request,
                 query_terminal_prop_request,
                 query_position_request,
                 link_detection,
                 driver_identity_report_request -> {

            }
            case query_server_time_response -> {
                processor_queryServerTimeResponse.deProcess(data, processContext, (QueryServerTimeResponse) instance);
            }
            case server_sub_packet_request, terminal_sub_packet_request -> {
                processor_serverSubPacketRequest.deProcess(data, processContext, (SubPacketRequest) instance);
            }
            case terminal_register_request -> {
                ((TerminalRegisterRequest) instance).write(data);
            }
            case terminal_register_response -> {
                ((TerminalRegisterResponse) instance).write(data);
            }
            case terminal_authentication -> {
                processor_terminalAuthentication.deProcess(data, processContext, (TerminalAuthentication) instance);
            }
            case set_terminal_param -> {
                processor_setTerminalParam.deProcess(data, processContext, (SetTerminalParam) instance);
            }
            case query_terminal_specify_param_request -> {
                processor_queryTerminalSpecifyParamRequest.deProcess(data, processContext, (QueryTerminalSpecifyParamRequest) instance);
            }
            case query_terminal_param_response -> {
                processor_queryTerminalParamResponse.deProcess(data, processContext, (QueryTerminalParamResponse) instance);
            }
            case terminal_control -> {
                ((TerminalControl) instance).write(data);
            }
            case query_terminal_prop_response -> {
                processor_queryTerminalPropResponse.deProcess(data, processContext, (QueryTerminalPropResponse) instance);
            }
            case issued_terminal_upgrade_request -> {
                processor_issuedTerminalUpgradeRequest.deProcess(data, processContext, (IssuedTerminalUpgradeRequest) instance);
            }
            case terminal_upgrade_res_response -> {
                processor_terminalUpgradeResResponse.deProcess(data, processContext, (TerminalUpgradeResResponse) instance);
            }
            case position_data_upload -> {
                ((Position) instance).write(data);
            }
            case query_position_response -> {
                ((QueryPositionResponse) instance).write(data);
            }
            case temp_position_follow -> {
                processor_tempPositionFollow.deProcess(data, processContext, (TempPositionFollow) instance);
            }
            case confirm_alarm_msg -> {
                processor_confirmAlarmMsg.deProcess(data, processContext, (ConfirmAlarmMsg) instance);
            }
            case text_info_issued -> {
                ((TextInfoIssued) instance).write(data);
            }
            case phone_callback -> {
                ((PhoneCallback) instance).write(data);
            }
            case set_phone_text -> {
                processor_setPhoneText.deProcess(data, processContext, (SetPhoneText) instance);
            }
            case vehicle_control_request -> {
                ((VehicleControlRequest) instance).write(data);
            }
            case vehicle_control_response -> {
                ((VehicleControlResponse) instance).write(data);
            }
            case set_circle_area -> {
                ((SetCircleArea) instance).write(data);
            }
            case delete_circle_area -> {
                processor_deleteCircleArea.deProcess(data, processContext, (DeleteCircleArea) instance);
            }
            case set_rectangle_area -> {
                ((SetRectangleArea) instance).write(data);
            }
            case delete_rectangle_area -> {
                processor_deleteRectangleArea.deProcess(data, processContext, (DeleteRectangleArea) instance);
            }
            case set_polygon_area -> {
                ((SetPolygonArea) instance).write(data);
            }
            case delete_polygon_area -> {
                processor_deletePolygonArea.deProcess(data, processContext, (DeletePolygonArea) instance);
            }
            case set_path -> {
                ((SetPath) instance).write(data);
            }
            case delete_path -> {
                processor_deletePath.deProcess(data, processContext, (DeletePath) instance);
            }
            case query_area_or_path_request -> {
                processor_queryAreaOrPathRequest.deProcess(data, processContext, (QueryAreaOrPathRequest) instance);
            }
            case query_area_or_path_response -> {
                ((QueryAreaOrPathResponse) instance).write(data);
            }
            case driving_recorder_collect_command, driving_recorder_download_command -> {
                ((DrivingRecorderCollectCommand) instance).write(data);
            }
            case driving_recorder_upload -> {
                ((DrivingRecorderUpload) instance).write(data);
            }
            case waybill_report -> {
                processor_waybillReport.deProcess(data, processContext, (WaybillReport) instance);
            }
            case driver_identity_report_response -> {
                ((DriverIdentityReportResponse) instance).write(data);
            }
            case position_data_batch_upload -> {
                ((PositionDataBatchUpload) instance).write(data);
            }
            case can_data_upload -> {
                processor_canDataUpload.deProcess(data, processContext, (CanDataUpload) instance);
            }
            case multimedia_event_upload -> {
                processor_multiMediaEventUpload.deProcess(data, processContext, (MultimediaEventUpload) instance);
            }
            case multimedia_data_upload_request -> {
                ((MultimediaDataUploadRequest) instance).write(data);
            }
            case multimedia_data_upload_response -> {
                ((MultimediaDataUploadResponse) instance).write(data);
            }
            case camera_take_photo_cmd_request -> {
                processor_cameraTakePhotoCmdRequest.deProcess(data, processContext, (CameraTakePhotoCmdRequest) instance);
            }
            case camera_take_photo_cmd_response -> {
                processor_cameraTakePhotoCmdResponse.deProcess(data, processContext, (CameraTakePhotoCmdResponse) instance);
            }
            case storage_multimedia_data_fetch_request -> {
                processor_storageMultiMediaDataFetchRequest.deProcess(data, processContext, (StorageMultimediaDataFetchRequest) instance);
            }
            case storage_multimedia_data_fetch_response -> {
                ((StorageMultimediaDataFetchResponse) instance).write(data);
            }
            case storage_multimedia_data_upload_cmd -> {
                processor_storageMultiMediaDataUploadCmd.deProcess(data, processContext, (StorageMultimediaDataUploadCmd) instance);
            }
            case recording_start_cmd -> {
                processor_recordingStartCmd.deProcess(data, processContext, (RecordingStartCmd) instance);
            }
            case single_multimedia_data_fetch_upload_cmd -> {
                processor_singleMultiMediaDataFetchUploadCmd.deProcess(data, processContext, (SingleMultimediaDataFetchUploadCmd) instance);
            }
            case data_down_stream -> {
                ((DataDownStream) instance).write(data);
            }
            case data_up_stream -> {
                ((DataUpStream) instance).write(data);
            }
            case data_compress_report -> {
                ((DataCompressReport) instance).write(data);
            }
            case platform_rsa -> {
                processor_platformRsa.process(data, processContext);
            }
            case terminal_rsa -> {
                processor_terminalRsa.process(data, processContext);
            }
            default -> throw BaseException.get("msgId[{}] not support", packet.header.msgId);
        }
    }

}
