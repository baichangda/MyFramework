package cn.bcd.server.simulator.singleVehicle.tcp;

import io.undertow.server.handlers.PathHandler;

public interface HttpServerBuilder {
    void build(PathHandler pathHandler);
}
