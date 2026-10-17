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

## 参数数量

2016、2025 两版的 `ParamQueryResponse` 和 `ParamSetRequest` 均通过
`paramData.num`、`paramData.num__v` 公开参数数量及其异常标记。
解析时由 `ParamDataProcessor` 一起读取数量和参数；正常状态下编码会根据实际输出
更新 `paramData.num` 并回填报文中的数量，无需手动填写。域名长度和域名内容各算一个参数。
数值非空或对应 `__v` 非零时，该数值参数会被输出，异常值不会丢失。
数量的 `num__v` 为 1/2 时只编码 `FF`/`FE` 数量标记，不输出参数，`num` 归零。

原先访问外层 `num`、`num__v` 的调用方需要改为访问 `paramData` 中的字段；
JSON 字段位置随之调整，二进制报文格式保持不变。

## 验证

```shell
gradle :Lib-Parser-Protocol-GB32960:test
```
