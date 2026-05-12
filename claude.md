# 角色定义

此文件中所有的信息、你代表AI、我是用户即使用者

你是一个资深软件工程师、在我搭建好的项目框架下开发应用

# 框架概述

项目相关信息

- 项目根目录下Lib-开头文件夹的代表是依赖模块、无法启动

- 项目根目录下App-开头的文件夹代表是应用模块、可以bootJar打包运行

- 依赖版本管理在项目根目录下的gradle/libs.versions.toml中

# 前置条件

在你进行开发之前、必须检查项目环境、确保是正确的、如果发现有缺失、你必须告知我完善才能继续

- java、gradle已经全局预装好了、直接使用即可、你需要做的就是检查是否可用、如果不可用、提示用户安装即可、等待安装完毕后继续
  
  - 这些命令的执行你不需要询问我

- 项目中包含模块Lib-Base

- 项目根目录下包含文件.gitignore、build.gradle、setting.gradle

- 在项目目录下执行gradle build -x test成功

# 开发流程定义

你进行应用开发应该遵循一定的工作流程、推进的过程应该是渐进式的、一步一步推进、以下是应该遵循的流程、你也可以根据任务的特殊性加一下流程在其中

- 先进行初步需求收集、主要目的是为了制定应用模块名称、和我确认、然后创建

- 工作目录设置为应用模块文件夹、后续所有的命令操作都在此文件夹下执行、除非我特别指定执行的文件夹

- 建立build.gradle、引入Lib-Base、后续按需修改依赖

- 在建立doc文件夹、后续所有的文档都放在其中

- 详细需求收集、需求收集遵循如下几点
  
  - 收集详细的需求、建立需求文档requirements.md、在收集的过程中完善
  
  - 在收集完毕后告知我、汇报给我、我确认之后在进行下一步、汇报内容包含如下信息
    
    - 需求方案设计依据
    
    - 需求划分为哪些功能、每个功能的详细描述
    
    - 每个描述点必须要有编号、例如1.1、1.2、1.1.1、方便我通过编号指出哪项需要修改

- 技术方案选型、选型要遵循如下几点
  
  - 制定实现的技术方案、建立技术文档technical.md、在和我确认过程中完善
  
  - 技术方案选型技术时候、优先考虑libs.versions.toml中定义的技术
  
  - 技术方案选型要充分参考Lib模块中是否已经有对应工具类或组件、优先使用
  
  - 日志使用log4j2、日志输出级别默认是INFO、参考如下
    
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    
    <!--Configuration后面的status，这个用于设置log4j2自身内部的信息输出，可以不设置，当设置成trace时，你会看到log4j2内部各种详细输出-->
    
    <!--monitorInterval：Log4j能够自动检测修改配置 文件和重新配置本身，设置间隔秒数-->
    
    <Configuration monitorInterval="5">
        <!--日志级别以及优先级排序: OFF > FATAL > ERROR > WARN > INFO > DEBUG > TRACE > ALL -->
    
        <!--变量配置-->
        <Properties>
            <Property name="APP_NAME">data</Property>
            <Property name="LOG_DIR">logs</Property>
            <!-- 格式化输出：%date表示日期，%thread表示线程名，%-5level：级别从左显示5个字符宽度 %msg：日志消息，%n是换行符-->
            <Property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"/>
        </Properties>
    
        <Appenders>
            <Console name="Console" target="SYSTEM_OUT">
                <!--输出日志的格式-->
                <PatternLayout pattern="${LOG_PATTERN}"/>
            </Console>
            <RollingFile name="RollingFile"
                         filename="${LOG_DIR}/${APP_NAME}.log"
                         filePattern="${LOG_DIR}/${APP_NAME}-%d{yyyyMMdd}.log.gz">
                <PatternLayout pattern="${LOG_PATTERN}"/>
                <!-- Policies :日志滚动策略-->
                <Policies>
                    <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
                </Policies>
                <DefaultRolloverStrategy>
                    <Delete basePath="${LOG_DIR}" maxDepth="1">
                        <!-- 删除所有匹配正则的文件 -->
                        <IfFileName glob="${APP_NAME}-*.log.gz"/>
                        <IfLastModified age="30d"/>
                    </Delete>
                </DefaultRolloverStrategy>
            </RollingFile>
        </Appenders>
    
        <!--Logger节点用来单独指定日志的形式，比如要为指定包下的class指定不同的日志级别等。-->
        <!--然后定义loggers，只有定义了logger并引入的appender，appender才会生效-->
        <Loggers>
            <!-- 日志输出级别 -->
            <Root level="INFO">
                <Appender-ref ref="Console"/>
                <Appender-ref ref="RollingFile"/>
            </Root>
        </Loggers>
    
    </Configuration>
    ```
  
  - 如果使用了springboot框架、则优先技术使用其全家桶
  
  - 项目框架已经包含了lombok插件
  
  - 技术选型完毕之后、和我对齐一下用了哪些技术栈、我会根据你的技术栈、加入一些新的Lib-模块给你用(不一定会有)、然后再根据我加入的Lib-做方案调整
  
  - 所有事情做完后、汇报给我、我确认之后在进行下一步、汇报内容包含如下信息
    
    - 技术方案选型的依据
    
    - 选用了哪些技术栈、技术栈如何集成进来
    
    - 针对每个功能给出具体的技术实现
    
    - 每个描述点必须要有编号、例如1.1、1.2、1.1.1、方便我通过编号指出哪项需要修改

- 在完成技术方案选型后、开始编码、编码遵循如下几点
  
  - 应用模块的目录结构前两级缀参考Lib-Base前两级、从后面开始接上应用模块名称(不带上App、应用名后面以-分割包名)、举例如下：
    
    例如App-a-b-c、前两级前缀为cn.bcd、则此项目完整目录为cn.bcd.a.b.c
  
  - 如果使用了spring boot搭建web应用、必须集成swagger
  
  - spring boot web应用目录结构遵循controller、service、bean 3层
    
    - bean存放数据库映射实体类、以Bean结尾命名格式、如果有其他实体类定义、在bean文件夹的同级目录下建立文件夹
    - service用于写业务逻辑、以Service结尾命名格式
    - controller用于写http服务、以Controller结尾命名格式
    - controller中、查询接口直接返回对应的实体类bean、不需要vo
    - controller中、新增和更新接口合并为一个保存接口、传入实体类bean、判断传入实体类bean的id是否为null来决定是新增还是更新、如果是null则新增、否则更新
    - bean的字段必须加上swagger注解
  
  - spring配置类、都放在文件夹prop中、其中属性类以Prop结尾、属性类是AppProp、对应属性开头是app、后续如果需要添加熟悉、可以创建Prop结尾的属性对象作为叶子节点加入到app下面
  
  - 如果是spring boot应用、则启动入口为Application.java、且在cn.bcd目录下
  
  - 如果使用了sqlite、生成数据库文件放在应用模块目录下的db文件夹下
  
  - 如果有前端静态页面、在必须在resources/static下面
  
  - 如果存在需要我提供配置、在application.yml中提前预留好、让我填入
  
  - 业务异常使用Lib-Base下的类BaseException
  
  - 数据库设计schema必须加上注释、优先使用数据库语法注释
  
  - 数据库表设计必须以t_开头
  
  - 如果是web服务、且包含前端静态页面服务、应该遵循前后端分离的模式
  
  - 数据库表主键尽可能使用自增长数字id、除非特殊场景才使用uuid
  
  - 数据库表不要定义外键约束、在列注释中写清除即可
  
  - 对于提供的web服务、所有返回前端的数据必须是Lib-Base下的Result类、必须有全局的异常处理、异常也转换为Result类
  
  - 所有的代码必须有详细注释
  
  - 在一切确定之后、汇报给我、我确认之后在进行编码、汇报的内容如下
    
    - 应用模块下完整的目录结构、每个目录结构是做什么的
    
    - 每个目录结构下有哪些类文件、每个类的大致用途
    
    - 每个描述点必须要有编号、例如1.1、1.2、1.1.1、方便我通过编号指出哪项需要修改

- 在完成编码之后、你需要进行测试、遵循如下
  
  - 测试需要覆盖各个功能、如果某些功能难以测试、需要告知我
  
  - 给我按照功能列表输出一份测试结构文档test.md、文档中包含如下
    
    - 测试了哪些功能
    
    - 每个功能的测试方法、测试参数
  
  - 使用curl测试不需要询问我
  
  - 编写测试命令脚本临时测试执行、你不需要询问我
  
  - 测试用例应该尽量使用java语言编写、测试代码放在src/main/test下、且测试类的目录结构应该尽量保证和测试源代码一致
  
  - 所有的源码类不一定都需要有对应的测试类、只需要测试对外暴露的接口、遵循如下规则、例如：
    
    - 如果是web服务、则只需要覆盖测试所有的对外接口服务、而且只针对http接口进行测试、不对静态文件服务测试
  
  - 针对不同的接口测试应该准备不同的参数、尽量保证代码覆盖率
  
  - 测试类的注释应该详细、说明测试场景、测试方法
  
  - 如果遇到难以测试的场景、例如压力测试、应该询问我、由我来决定测试方法

- 在所有测试通过之后、告知我进行最终测试

# 任务中断恢复

因为某些原因任务对话会中断、所以你需要遵循如下

- 任务开始之前首先将工作目录设置为应用模块的目录、后续所有的命令操作都在此文件夹下执行、除非我特别指定执行的文件夹

- 在任务进行的过程中、将当前对话任务进度存入到模块应用目录下的doc/task.md中、并随着对话不断更新此进度文件、这样如果因为异常原因中断、可以借助此文件恢复对话

- 当识别到我关于继续或者恢复之前任务进度时候、你需要按照以下流程开展工作
  
  - 首先读取项目根目录下面的claude.md理解我制定的规范和约束
  
  - 然后进入模块应用目录下doc中、读取其中的文档、了解应用信息、有如下几个文档
    
    - task.md 任务进度文档、用于给你恢复之前中断的对话
    
    - requirements.md 需求说明文档
    
    - technical.md 技术文档
    
    - test.md 测试文档

# 需求变更

在接收到我的需求变更之后、应该遵循如下

- 首先收集需求、收集完毕后和我确认、确认后在执行

- 修改完代码后、需要进行测试、先梳理影响范围、需要运行测试用例、然后和我确认后在执行

- 测试通过后、更新doc下面的所有文档(如果有必要)

# 注意事项

- 在任务进行的过程中、要随时根据需求、进度变化更新所有文档、时刻保证代码和文档的一致性

- 你要明确区分项目根目录、当前任务的应用模块目录

- 在执行过程中如果我的命令和规则冲突、则告诉我、由我来决定

- 核心的目的是保证整个过程可视化

- 所有需要我确认的点、必须说明为什么需要确认以及确认什么

- 所有需要我确认的点、如果存在多个确认项、必须给出编号1、2、3、方便我确认

- 你对项目目录下的所有文件及文件夹拥有绝对权限、无需询问我

- 所有的命令操作你都可以执行、无需询问我、例外如下
  
  - 如果有git、不允许使用git更新、提交、推送、回滚等能修改本地云端代码的操作、但是你可以使用git的查看功能、git的查看功能不需要询问
