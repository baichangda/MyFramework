# Lib-Spring-Data-Init 使用指南

## 功能

在 Spring 服务启动时从业务服务加载权限、车辆、转发配置和转发访问关系，并维护本地缓存。服务发现通过 Nacos HTTP API 完成，数据变化通过 `Lib-Spring-Data-Notify` 更新。

## 引入与配置

```groovy
implementation project(':Lib-Spring-Data-Init')
```

```yaml
lib:
  spring:
    data:
      init:
        nacos-host: 127.0.0.1
        nacos-port: 8848
        permission:
          enable: true
```

当前组件分别通过 `permission.enable`、`vehicle.enable`、`transferConfig.enable` 和 `transferAccess.enable` 条件启用。启用前应核对 `InitProp` 中对应配置对象名称，并确保业务后端已注册到 Nacos。

## 使用缓存

按需读取 `PermissionDataInit.resource_permission`、`VehicleDataInit.vin_vehicleData`、`TransferAccessDataInit.vin_platformCodes`，或调用 `TransferConfigDataInit.get(serverId)`。这些缓存为进程内状态，启动初始化完成前不要依赖其内容。
