# Lib-Parser-Base 使用指南

## 功能

基于字段注解动态生成高性能二进制编解码器。核心入口为 `Parser.getProcessor(Class)`，字段可使用 `@F_num`、`@F_string`、`@F_bean`、`@F_customize` 等注解描述布局。

## 引入与示例

```groovy
implementation project(':Lib-Parser-Base')
```

```java
class Message {
    @F_num(type = NumType.uint16)
    public int id;
}

Processor<Message> processor = Parser.getProcessor(Message.class);
Message message = processor.process(inputByteBuf);
processor.deProcess(outputByteBuf, message);
```

可通过重载方法指定 `ByteOrder` 和 `NumValGetter`。解析日志、反解析日志及生成类文件等选项必须在第一次获取处理器前开启；首次获取后配置会被冻结。

## 注意事项

处理器会缓存。仅在测试或确需重建时调用 `Parser.clearProcessorCache()`。`disableByteBufCheck()` 会关闭 Netty 边界保护，只应在输入可信且性能测试证明有收益时使用。

```shell
gradle :Lib-Parser-Base:test
```
