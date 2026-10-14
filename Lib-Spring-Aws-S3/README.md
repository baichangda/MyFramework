# Lib-Spring-Aws-S3 使用指南

## 功能

基于 AWS SDK v2 提供 S3 客户端自动配置和 `AwsS3Util`，支持列举、上传、分片上传、下载、删除及对象存在性检查，也适用于兼容 S3 协议的对象存储。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Aws-S3')
```

```yaml
lib:
  spring:
    aws:
      s3:
        endpoint: http://127.0.0.1:9000
        region: us-east-1
        access-key: ${S3_ACCESS_KEY}
        secret-key: ${S3_SECRET_KEY}
        force-path-style: true
        bucket: demo
```

配置 `endpoint` 后组件才会启用。确保应用扫描 `cn.bcd.lib.spring.aws.s3`。

```java
AwsS3Util.putObject(Path.of("report.csv"), "reports/report.csv");
byte[] content = AwsS3Util.getObject("reports/report.csv");
```

大文件使用 `putObjectLarge`。凭据只应通过环境变量或密钥服务提供。

`getObject(key)` 会将整个对象读入内存，仅适用于小对象。大对象请使用输入流回调或文件下载：

```java
boolean downloaded = AwsS3Util.getObject("reports/report.csv", "report.csv");
AwsS3Util.listObjects("reports/", true, key -> System.out.println(key));
```

文件下载返回 `true` 表示成功，返回 `false` 表示对象不存在；其他错误仍抛出异常。该返回类型由 `void` 改为 `boolean`，已有调用源码可继续忽略返回值，但依赖该接口的模块需要重新编译。

带回调的 `listObjects` 逐个处理 key，避免收集完整对象列表。目录上传边遍历边上传，不跟随目录符号链接，也不上传符号链接文件。分片上传复用一个缓冲区，支持空流，并为每次重试提供独立输入流；分片仍按顺序上传，默认缓冲区为 16 MiB。

离线回归测试：

```text
gradle :Lib-Spring-Aws-S3:test --tests cn.bcd.lib.spring.aws.s3.AwsS3UtilTest --no-daemon
```
