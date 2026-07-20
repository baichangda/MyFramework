# Lib-Parser-Protocol-Immotors 使用指南

## 功能

提供 IM Motors 事件流二进制模型与解析逻辑。`Packet` 会按事件编号解析 `Evt_0001`、`Evt_D006` 等已支持事件，并保留部分未知事件类型的数据结构。

## 引入与解析

```groovy
implementation project(':Lib-Parser-Protocol-Immotors')
```

```java
import cn.bcd.lib.parser.protocol.immotors.data.Packet;

Packet packet = Packet.read(inputByteBuf);
if (packet.evt_0001 != null) {
    // 处理 0x0001 事件
}
```

调用前应确保 `ByteBuf` 中包含该协议期望的完整事件数据。解析会推进 `readerIndex`；需要重复读取时先复制或保存索引。Netty `ByteBuf` 的生命周期由调用方管理。

新增事件时，按现有 `Evt_*` 模型使用 `Lib-Parser-Base` 注解描述字段，并在 `Packet` 中注册相应处理器。

```shell
gradle :Lib-Parser-Protocol-Immotors:test
```
