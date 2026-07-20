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
