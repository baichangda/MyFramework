package cn.bcd.lib.spring.coap.server.resource;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;

public class HealthResource extends CoapResource {
    public HealthResource() {
        super("health");
        getAttributes().setTitle("CoAP server health");
        getAttributes().addContentType(MediaTypeRegistry.TEXT_PLAIN);
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        exchange.respond(ResponseCode.CONTENT, "UP", MediaTypeRegistry.TEXT_PLAIN);
    }
}
