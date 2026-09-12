# Lib-Websocket 使用指南

## 功能

基于 Vert.x Web 提供轻量 WebSocket 客户端与服务端封装。客户端支持自动连接、断线重连、文本发送、连接与消息回调；服务端可以按指定 URI 接收连接。

## 引入

```groovy
implementation project(':Lib-Websocket')
```

## 服务端示例

推荐使用 `start` 并处理异步启动结果，避免端口占用等错误仅在日志中出现：

```java
MyWebSocketServer.start(
        "0.0.0.0", 8080, "/ws",
        socket -> socket.textMessageHandler(System.out::println)
).onSuccess(server -> {
    // 服务已经开始监听
}).onFailure(cause -> {
    // 处理启动失败
});
```

保留了构造器形式以兼容已有代码；使用构造器时，通过 `startFuture()` 获取启动结果。应用停止时可以调用 `close()` 发起关闭，或者使用 `closeAsync()` 等待端口完全释放。

## 客户端示例

客户端支持标准 `ws://`、`wss://` URL，也兼容原有的 `host:port/path` 格式：

```java
MyWebSocketClient client = new MyWebSocketClient(
        "wss://example.com/ws",
        Duration.ofSeconds(2),
        message -> System.out.println(message),
        socket -> System.out.println("connected"),
        socket -> System.out.println("disconnected")
);

client.sendText("hello").whenComplete((ignored, cause) -> {
    if (cause != null) {
        // 处理发送失败
    }
});

client.closeAsync().onComplete(result -> {
    // 客户端资源已经释放
});
```

回调运行在 Vert.x 事件循环上，不要执行阻塞 IO。生产环境使用 `wss`、校验证书，并在上层实现认证和心跳。自动重连间隔不能小于 1 毫秒。

## 验证

```shell
gradle :Lib-Websocket:test
```
