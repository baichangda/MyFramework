# MyFramework

基于 Java 25、Gradle 和 Spring Boot 的多模块项目，包名统一使用 `cn.bcd`。

## 模块约定

- `App-*`：可独立启动和部署的应用，统一应用 Spring Boot 插件。
- `Lib-*`：供其他模块依赖的 Java 库，统一应用 `java-library` 和 `maven-publish` 插件。
- 子模块只需声明自身依赖及少量专属配置；Java、测试、源码包和发布等公共约定由根 `build.gradle` 管理。

主要应用模块：

- `App-BP-Auth`：业务认证服务。
- `App-BP-Backend`：业务后台服务。
- `App-DP-Gateway-Mqtt`：MQTT 数据网关。
- `App-DP-Gateway-Tcp`：TCP 数据网关。
- `App-DP-Parse`：数据解析服务。
- `App-DP-Transfer`：数据转发服务。
- `App-Monitor-Collector`：监控数据采集服务。
- `App-Simulator-PressTest-Tcp`：TCP 压力测试模拟器。
- `App-Simulator-SingleVehicle-Tcp`：TCP 单车模拟器。
- `App-Tool-Aws-S3-Client-Web`：AWS S3 客户端工具。
- `App-Tool-Kafka-Client-Web`：Kafka 客户端工具。
- `App-Transponder-GB32960`：GB32960 转发服务。

完整模块清单以 `settings.gradle` 为准。

## 依赖与仓库管理

依赖和插件版本统一定义在 `gradle/libs.versions.toml`，子模块通过 `libs.*` 引用。

插件仓库和依赖仓库统一配置在 `settings.gradle`：

- 插件仓库：阿里云 Gradle 插件仓库、Gradle Plugin Portal、Maven Central。
- 依赖仓库：阿里云公共仓库、Maven Central。
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` 禁止子模块自行声明依赖仓库。

默认不启用 Maven 本地仓库，以免本机缓存影响构建一致性。需要联调通过 `publishToMavenLocal` 发布的本地依赖时，可显式启用：

```cmd
gradle build -PuseMavenLocal
```

## 构建与测试

在仓库根目录执行：

```cmd
gradle clean build
gradle test
gradle :Lib-Base:test
gradle :App-BP-Backend:bootRun
gradle :App-BP-Backend:bootJar
```

应用打包时会排除 `application-local.yml`。本地配置文件不应提交或包含在部署产物中。

## Maven 发布

库模块统一生成 Maven publication 和 sources Jar。例如发布到 Maven 本地仓库：

```cmd
gradle :Lib-Base:publishToMavenLocal
```

远程仓库凭据不得写入构建脚本。可以通过用户级 `gradle.properties` 配置：

```properties
repoUsername=your-username
repoPassword=your-password
```

也可以使用环境变量：

```text
REPO_USERNAME
REPO_PASSWORD
```

例如发布业务后台模块：

```cmd
gradle :App-BP-Backend:publishMavenPublicationToMavenRepository
```

## Profiling

This project uses [JProfiler, a Java profiler](https://www.ej-technologies.com/jprofiler) for performance analysis.