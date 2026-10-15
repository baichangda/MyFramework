# Lib-Spring-Coap

基于 Eclipse Californium 3.14.0 的 Spring Boot CoAP 服务库。通过组件扫描加载配置，默认不启动服务。应用扫描 `cn.bcd` 即可覆盖本库；若使用其他扫描范围，请加入 `cn.bcd.lib.spring.coap.server`。

应用模块添加依赖：

```groovy
implementation project(":Lib-Spring-Coap")
```

在应用的 `application.yaml` 中启用：

```yaml
lib:
  spring:
    coap:
      server:
        host: 0.0.0.0
        port: 5683
        protocol-thread-count: 4
```

通过 `@ConditionalOnProperty` 检测 `host`，未配置或值为 `false` 时不启动；配置时应填写有效监听地址，空字符串不表示关闭。`port` 默认为 `5683`；协议处理线程数默认取可用处理器数，最少 2 个。端口为 `0` 时由操作系统分配临时端口。

业务应用将自己的 `CoapResource` 声明为 Spring Bean，例如：

```java
@Component
public class HelloResource extends CoapResource {
    public HelloResource() {
        super("hello");
    }

    @Override
    public void handleGET(CoapExchange exchange) {
        exchange.respond(CoAP.ResponseCode.CONTENT, "hello");
    }
}
```

服务通过 `@Autowired List<CoapResource>` 注入并注册资源；没有资源 Bean 时也可启动。业务代码同样可以注入该列表。资源名称作为根路径，如 `coap://127.0.0.1:5683/hello`；资源发现路径为 `/.well-known/core`。库本身不注册示例业务资源。

服务随 Spring 容器启停，关闭时销毁 endpoint 和内部线程池；端口绑定失败会终止应用启动。目前提供明文 UDP CoAP，未配置 DTLS。库不提供应用级日志或全局 application.yaml 配置。

验证及打包：

```shell
gradle :Lib-Spring-Coap:build
```

客户端使用 `cn.bcd.lib.spring.coap.client.CoapClient`，可独立于 Spring 和服务端配置使用：

```java
try (CoapClient client = CoapClient.builder("coap://127.0.0.1:5683/hello")
        .timeoutMillis(3000)
        .build()) {
    CoapResponse response = client.get();
}
```

客户端保留原生同步、异步、Observe 和请求配置 API。默认超时 5 秒、发送 Confirmable 请求，使用独立的 UDP endpoint 和临时本地端口。可通过 `localAddress(host, port)` 指定本地地址、`confirmable(false)` 使用 NON 请求、`configure(configuration -> ...)` 调整 Californium 参数。

`build()` 会启动 endpoint，端口绑定失败直接抛出 IOException。`close()` 和 `shutdown()` 均释放客户端内部资源及其独立 endpoint，支持重复调用。建议复用客户端，结束使用时关闭；本封装仅构造 UDP coap:// 客户端。
