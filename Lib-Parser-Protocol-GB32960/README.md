# Lib-Parser-Protocol-GB32960 使用指南

## 功能

实现 GB/T 32960 的 2016 与 2025 两套报文模型、数据单元处理器和校验工具。两个版本分别位于 `v2016`、`v2025` 包，请勿混用模型。

## 引入与解析

```groovy
implementation project(':Lib-Parser-Protocol-GB32960')
```

```java
import cn.bcd.lib.parser.protocol.gb32960.v2016.data.Packet;

Packet packet = Packet.read(inputByteBuf);
ByteBuf encoded = packet.toByteBuf_fixAll();
try {
    // 发送或复制 encoded
} finally {
    encoded.release();
}
```

`toByteBuf_fixAll()` 会修正数据单元长度和异或校验码；仅需修正校验码时使用 `toByteBuf_fixCode()`。输入必须包含完整帧，拆包和粘包应由上层网络处理器完成。

## 验证

```shell
gradle :Lib-Parser-Protocol-GB32960:test
```
