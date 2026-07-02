package cn.bcd.app.businessProcess.gateway;

import feign.codec.Decoder;
import feign.optionals.OptionalDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class FeignConfig {

    @Bean
    public Decoder feignDecoder(ObjectProvider<FeignHttpMessageConverters> messageConverters) {
        return new OptionalDecoder(
                new ResponseEntityDecoder(
                        new SpringDecoder(messageConverters)
                )
        );
    }

    /**
     * OpenFeign 专用的 HttpMessageConverter 定制器。
     *
     * 用来处理某些接口返回的是 JSON，
     * 但响应头却是 text/html;charset=UTF-8 的情况。
     */
    @Bean
    public HttpMessageConverterCustomizer gatewayFeignMessageConverterCustomizer() {
        return converters -> converters.addFirst(new GateWayMappingJackson2HttpMessageConverter());
    }

    public static class GateWayMappingJackson2HttpMessageConverter extends JacksonJsonHttpMessageConverter {

        public GateWayMappingJackson2HttpMessageConverter() {
            setSupportedMediaTypes(List.of(
                    MediaType.APPLICATION_JSON,
                    MediaType.valueOf("application/*+json"),
                    MediaType.TEXT_HTML,
                    MediaType.valueOf("text/html;charset=UTF-8")
            ));
        }
    }
}