# 分模块规则
- ***App-*** 开头的模块为可以单独部署启动的服务
- ***Lib-*** 开头的模块为依赖包、无法启动
- ***Bom模块*** 为所有依赖的版本统一管理、不包括插件、还有部分的版本定义在项目根目录的 ***gradle.build*** 中

# 服务列表
- ***App-AI-MCP-Server*** ai-mcp协议服务
- ***App-BusinessProcess-Backend*** 微服务业务后台
- ***App-BusinessProcess-Gateway*** 微服务网关
- ***App-BusinessProcess-Openapi*** 微服务开放式接口服务
- ***App-DataProcess-Gateway-Mqtt*** 数据层mqtt网关服务
- ***App-DataProcess-Gateway-Tcp*** 数据层tcp网关服务
- ***App-DataProcess-Parse*** 数据层解析服务
- ***App-DataProcess-Transfer*** 数据层转发服务
- ***App-Monitor-Collector*** 性能监控服务
- ***App-Simulator-PressTest-Tcp*** GB32960 TCP压测模拟器服务
- ***App-Simulator-SingleVehicle-Tcp*** GB32960 TCP单车模拟器服务
- ***App-Tool-Minio-Client-Web*** minio客户端工具服务
- ***App-Tool-Kafka-Client-Web*** kafka客户端工具服务
- ***App-Tool-Stock*** 股票分析工具