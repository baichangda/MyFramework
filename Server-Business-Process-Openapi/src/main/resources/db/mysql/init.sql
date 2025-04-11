CREATE TABLE IF NOT EXISTS t_openapi_user
(
    id               bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             varchar(50)     NOT NULL COMMENT '用户名',
    status           int             NOT NULL COMMENT '用户状态(1:启用;0:禁用)',
    secret_key       varchar(32)     not null COMMENT '密钥',
    remark           varchar(100)    null COMMENT '备注',
    create_time      datetime        NULL COMMENT '创建时间',
    create_user_id   bigint COMMENT '创建人id',
    create_user_name varchar(50) COMMENT '创建人姓名',
    update_time      datetime        NULL COMMENT '更新时间',
    update_user_id   bigint COMMENT '更新人id',
    update_user_name varchar(50) COMMENT '更新人姓名',
    PRIMARY KEY (id)
);

ALTER TABLE t_openapi_user
    COMMENT 'openapi用户表';
