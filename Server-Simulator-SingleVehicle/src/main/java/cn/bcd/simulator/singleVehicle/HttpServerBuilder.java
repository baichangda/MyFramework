package cn.bcd.simulator.singleVehicle;

import io.undertow.server.handlers.PathHandler;

public interface HttpServerBuilder {
    void build(PathHandler pathHandler);
}
