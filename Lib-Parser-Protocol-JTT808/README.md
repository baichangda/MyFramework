# Lib-Parser-Protocol-JTT808 使用指南

## 功能

实现 JT/T 808-2019 消息头、消息体、分包结构及转义/校验逻辑。消息体类型集中在 `cn.bcd.lib.parser.protocol.jtt808.v2019.data`。

## 引入与处理

```groovy
implementation project(':Lib-Parser-Protocol-JTT808')
```

接收到完整的 `0x7e` 帧后先反转义并校验，再使用基础解析器读取：

```java
ByteBuf plain = Packet.unEscapeAndXor(frame);
if (plain == null) {
    return; // 转义或校验失败
}
Processor<Packet> processor = Parser.getProcessor(Packet.class);
Packet packet = processor.process(plain);
```

发送前构造 `Packet` 并通过 Processor 写入缓冲区，随后调用 `Packet.xorAndEscape(...)`。网络层负责半包、粘包和分包重组；调用方负责释放新创建的 `ByteBuf`。

```shell
gradle :Lib-Parser-Protocol-JTT808:test
```
