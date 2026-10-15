package cn.bcd.lib.spring.coap.server.resource;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;

public class EchoResource extends CoapResource {
    public EchoResource() {
        super("echo");
        getAttributes().setTitle("Echo POST payload");
    }

    @Override
    public void handlePOST(CoapExchange exchange) {
        int contentFormat = exchange.getRequestOptions().hasContentFormat()
                ? exchange.getRequestOptions().getContentFormat() : MediaTypeRegistry.APPLICATION_OCTET_STREAM;
        exchange.respond(ResponseCode.CONTENT, exchange.getRequestPayload(), contentFormat);
    }
}
