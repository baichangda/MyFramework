package cn.bcd.app.simulator.singleVehicle.tcp.gb32960;

public abstract class VehicleData {

    public final String vin;

    public VehicleData(String vin) {
        this.vin = vin;
    }

    public void init() {
        init_vehicleRunData();
    }

    public abstract void init_vehicleRunData();

    public abstract byte[] onSend_vehicleRunDataToBytes();
}
