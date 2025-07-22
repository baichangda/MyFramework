CREATE TABLE IF NOT EXISTS t_monitor_data
(
    id          bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    server_id   varchar(50)     NOT NULL COMMENT '服务id',
    server_type int             NOT NULL DEFAULT 1 COMMENT '服务类型',
    batch       bigint UNSIGNED NOT NULL COMMENT '采集批次',
    status      int             NOT NULL DEFAULT 1 COMMENT '服务状态(0:正常;1:故障)',
    system_data TEXT COMMENT '系统信息',
    ext_data    TEXT COMMENT '附加数据',
    PRIMARY KEY (id)
);

ALTER TABLE t_monitor_data
    COMMENT '采集的监控信息数据表';


CREATE TABLE IF NOT EXISTS t_server_data
(
    server_id   varchar(50) NOT NULL COMMENT '服务id',
    server_type int         NOT NULL DEFAULT 1 COMMENT '服务类型',
    PRIMARY KEY (server_id)
);
ALTER TABLE t_monitor_data
    COMMENT '监控服务数据表';