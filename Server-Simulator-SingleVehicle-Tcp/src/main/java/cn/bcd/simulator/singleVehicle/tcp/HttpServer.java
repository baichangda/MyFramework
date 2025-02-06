package cn.bcd.simulator.singleVehicle.tcp;

import cn.bcd.base.exception.BaseException;
import cn.bcd.simulator.singleVehicle.tcp.gb32960.HttpServerBuilder_gb32960;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "singleVehicle", mixinStandardHelpOptions = true)
public class HttpServer implements Runnable {

    List<HttpServerBuilder> handlers = List.of(
            new HttpServerBuilder_gb32960()
    );

    public final static HandlerWrapper encodinghandlerWrapper = new EncodingHandler.Builder().build(null);
    public final static HandlerWrapper accessLogHandler = new AccessLogHandler.Builder().build(Map.of("format", "common", "category", "bcd"));

    @CommandLine.Option(names = {"-p", "--httpServerPort"}, description = "http server port", required = false, defaultValue = "45678", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    int httpServerPort;

    @Override
    public void run() {
        try (ResourceManager resourceManager = new ClassPathResourceManager(HttpServer.class.getClassLoader(), "simulator/singleVehicle/tcp/common")) {
            ResourceHandler resourceHandler = new ResourceHandler(resourceManager);
            PathHandler pathHandler = Handlers.path()
                    .addPrefixPath("/common", resourceHandler);
            for (HttpServerBuilder handler : handlers) {
                handler.build(pathHandler);
            }
            Undertow.builder().addHttpListener(httpServerPort, "0.0.0.0")
                    .setHandler(accessLogHandler.wrap(encodinghandlerWrapper.wrap(pathHandler)))
                    .build()
                    .start();
        } catch (IOException ex) {
            throw BaseException.get(ex);
        }
    }
}
