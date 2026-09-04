package cn.bcd.app.dataProcess.transfer.v2016.handler;

import cn.bcd.lib.spring.kafka.ext.datadriven.WorkExecutor;
import cn.bcd.lib.spring.data.notify.onlyNotify.vehicleData.VehicleData;
import lombok.Data;

import java.util.Date;

@Data
public class Context {
    public WorkExecutor executor;
    public VehicleData vehicleData;

    public Date gwInTime;
    public Date gwOutTime;
    public Date parseInTime;
    public Date parseOutTime;
    public Date transferInTime;

    //最后一包实时数据转发时间
    public long lastRunReportTime;
}
