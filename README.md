# 分模块规则
- ***Server-*** 开头的模块为可以单独部署启动的服务
- ***Lib-*** 开头的模块为依赖包、无法启动
- ***Bom模块*** 为所有依赖的版本统一管理、不包括插件、还有部分的版本定义在项目根目录的 ***gradle.build*** 中

# 服务列表
- ***Server-Business-Process-Backend*** 微服务业务后台
- ***Server-Business-Process-Gateway*** 微服务网关
- ***Server-Business-Process-Openapi*** 微服务开放式接口服务
- ***Server-Data-Process-Gateway-Tcp*** 数据层网关服务
- ***Server-Data-Process-Parse*** 数据层解析服务
- ***Server-Data-Process-Transfer*** 数据层转发服务
- ***Server-Monitor-Collector*** 性能监控服务
- ***Server-Simulator-SingleVehicle-Tcp*** TCP单车模拟器服务
- ***Server-Simulator-PressTest-Tcp*** TCP压测模拟器服务
- ***Server-Tool-Minio-Client-Server*** minio客户端工具服务