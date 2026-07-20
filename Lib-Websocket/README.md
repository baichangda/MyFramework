# Lib-Websocket 使用指南

## 功能

基于 Vert.x Web 提供轻量 WebSocket 客户端与服务端封装。客户端支持自动连接、文本发送、连接与消息回调；服务端按指定 URI 接收连接。

## 引入

```groovy
implementation project(':Lib-Websocket')
```

## 服务端示例

```java
MyWebSocketServer server = new MyWebSocketServer(
        "0.0.0.0", 8080, "/ws",
        socket -> socket.textMessageHandler(System.out::println));

// 应用停止时调用
server.close();
```

客户端通过 `MyWebSocketClient` 构造器传入 URL、连接处理器、消息处理器和异常处理器，连接完成后使用 `sendText(text)`，并处理返回的 `CompletableFuture`。应用关闭时调用 `close()`。

回调运行在 Vert.x 事件循环上，不要执行阻塞 IO。生产环境使用 `wss`、校验证书，并在上层实现认证、心跳和重连策略。

```shell
gradle :Lib-Websocket:build
```
