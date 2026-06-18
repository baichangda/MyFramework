# Repository Guidelines

## Project Structure & Module Organization
本仓库是 Gradle 多模块 Java/Spring 项目。`App-*` 模块是可独立部署启动的服务，通常包含 `src/main/java`、`src/main/resources` 和可选的 `src/test/java`。`Lib-*` 模块是共享依赖库，不直接启动。根目录的 `settings.gradle` 管理模块列表，`build.gradle` 统一配置构建逻辑，`gradle/libs.versions.toml` 管理依赖版本。服务配置、SQL、日志配置和静态资源放在各模块的 `src/main/resources`。

## Build, Test, and Development Commands
在仓库根目录使用全局安装的 JDK、Gradle 和 Git。

- `gradle clean build`: 编译所有模块、运行测试并生成构建产物。
- `gradle test`: 运行全部 JUnit 5 测试。
- `gradle :Lib-Base:test`: 只运行指定模块测试。
- `gradle :App-BusinessProcess-Backend:bootRun`: 本地启动指定 Spring Boot 服务。
- `gradle :App-BusinessProcess-Backend:bootJar`: 打包指定可部署服务 jar。

`Lib-*` 默认生成普通 jar，`App-*` 默认生成 Spring Boot jar。`application-local.yml` 会从 boot jar 中排除。

## Coding Style & Naming Conventions
Java 目标版本为 Java 25，包名根路径使用 `cn.bcd`。模块命名遵循现有模式，例如 `App-Domain-Name` 和 `Lib-Technology-Name`。代码使用 4 空格缩进，类名使用 PascalCase，字段和方法使用 camelCase，常量使用 UPPER_SNAKE_CASE。优先沿用项目中已有的 Spring、Lombok、MapStruct 写法。除非确有必要，不要在模块 `build.gradle` 中硬编码依赖版本，应维护 `gradle/libs.versions.toml`。

## Testing Guidelines
测试使用 JUnit Jupiter，并通过 Gradle 的 `useJUnitPlatform()` 执行。测试代码放在对应模块的 `src/test/java`，测试类命名为 `*Test`，例如 `DateUtilTest`、`LocalRateControlUnitTest`。库逻辑优先补充聚焦的单元测试；只有服务配置或外部行为变化时再增加集成类测试。提交前至少运行受影响模块的测试任务。

## Commit & Pull Request Guidelines
当前 Git 历史中提交信息较短，例如 `c`，没有形成有效约定。后续提交建议使用简洁、明确的描述，例如 `fix executor shutdown race` 或 `add parser boundary tests`。Pull Request 应说明影响的模块、已运行的验证命令、配置或数据库变更，并关联相关 issue。只有涉及 Web 界面变化时才需要截图。

## Agent-Specific Instructions
当前 Windows 工作区磁盘处于加密状态，查看和操作文件时使用 `cmd` 指令。避免使用 PowerShell 专用的文件系统命令。
