# Lib-Base 使用指南

## 功能

提供各模块通用的基础能力，包括统一返回值 `Result`、`BaseException`、本地限流器 `LocalRateControlUnit`，以及日期、十六进制、压缩、加密、IO、地理位置等工具类。

## 引入

同仓库模块在 `build.gradle` 中声明：

```groovy
implementation project(':Lib-Base')
```

该模块会传递 SLF4J、Guava 和 Bouncy Castle 依赖。

## 快速使用

```java
import cn.bcd.lib.base.util.HexUtil;
byte[] bytes = HexUtil.decodeHexDump("010AFF");
String hex = HexUtil.hexDump(bytes);
```

按需从 `cn.bcd.lib.base.util` 引入工具类，不要在业务模块重复实现相同能力。异常包装优先使用 `BaseException.get(...)`，接口响应可使用 `Result`。

## 注意事项

密码学工具需要调用方明确密钥格式、字符集和算法参数；不要将密钥写入源码。修改后运行：

```shell
gradle :Lib-Base:test
```
