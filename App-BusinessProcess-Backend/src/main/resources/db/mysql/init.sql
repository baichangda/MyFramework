CREATE TABLE IF NOT EXISTS t_sys_user
(
    id               bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username         varchar(50)     NOT NULL COMMENT '用户名',
    password         varchar(100)    NOT NULL COMMENT '密码',
    email            varchar(100) COMMENT '邮箱',
    phone            varchar(11) COMMENT '手机号',
    real_name        varchar(50) COMMENT '真实姓名',
    sex              varchar(5) COMMENT '性别',
    birthday         datetime(3)        NULL COMMENT '生日',
    card_number      varchar(20) COMMENT '身份证号',
    status           int             NOT NULL DEFAULT 0 COMMENT '是否可用(1:可用,0:禁用)',
    create_time      datetime(3)        NULL COMMENT '创建时间',
    create_user_id   bigint COMMENT '创建人id',
    create_user_name varchar(50) COMMENT '创建人姓名',
    update_time      datetime(3)        NULL COMMENT '更新时间',
    update_user_id   bigint COMMENT '更新人id',
    update_user_name varchar(50) COMMENT '更新人姓名',
    PRIMARY KEY (id)
);

ALTER TABLE t_sys_user
    COMMENT '用户基础信息表';

CREATE TABLE IF NOT EXISTS t_sys_role
(
    id               bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             varchar(20)     NOT NULL COMMENT '角色名称',
    code             varchar(50)     NOT NULL COMMENT '编码',
    remark           varchar(256) COMMENT '备注',
    create_time      datetime(3)        NOT NULL COMMENT '创建时间',
    create_user_id   bigint COMMENT '创建人id',
    create_user_name varchar(50) COMMENT '创建人姓名',
    update_time      datetime(3)        NULL COMMENT '更新时间',
    update_user_id   bigint COMMENT '更新人id',
    update_user_name varchar(50) COMMENT '更新人姓名',
    PRIMARY KEY (id)
);

ALTER TABLE t_sys_role
    COMMENT '角色表';

CREATE TABLE IF NOT EXISTS t_sys_menu
(
    id               bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id        bigint COMMENT '父菜单id',
    name             varchar(50)     NOT NULL COMMENT '菜单名称',
    url              varchar(256) COMMENT 'url地址',
    icon             varchar(256) COMMENT '图标',
    order_num        int             NOT NULL COMMENT '排序',
    create_time      datetime(3)        NOT NULL COMMENT '创建时间',
    create_user_id   bigint COMMENT '创建人id',
    create_user_name varchar(50) COMMENT '创建人姓名',
    update_time      datetime(3)        NULL COMMENT '更新时间',
    update_user_id   bigint COMMENT '更新人id',
    update_user_name varchar(50) COMMENT '更新人姓名',
    PRIMARY KEY (id)
);

ALTER TABLE t_sys_menu
    COMMENT '菜单表';


CREATE TABLE IF NOT EXISTS t_sys_permission
(
    id               bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
    name             varchar(20)     NOT NULL COMMENT '权限名称',
    resource         varchar(100)    NOT NULL COMMENT '资源',
    create_time      datetime(3)        NOT NULL COMMENT '创建时间',
    create_user_id   bigint COMMENT '创建人id',
    create_user_name varchar(50) COMMENT '创建人姓名',
    update_time      datetime(3)        NULL COMMENT '更新时间',
    update_user_id   bigint COMMENT '更新人id',
    update_user_name varchar(50) COMMENT '更新人姓名',
    PRIMARY KEY (id)
);

ALTER TABLE t_sys_permission
    COMMENT '角色与权限关系表';

CREATE TABLE IF NOT EXISTS t_sys_user_role
(
    user_id bigint COMMENT '关联用户id',
    role_id bigint COMMENT '关联角色id',
    PRIMARY KEY (user_id, role_id)
);

ALTER TABLE t_sys_user_role
    COMMENT '用户与角色关联关系表';

CREATE TABLE IF NOT EXISTS t_sys_role_menu
(
    role_id bigint COMMENT '关联角色id',
    menu_id bigint COMMENT '关联菜单id',
    PRIMARY KEY (role_id, menu_id)
);

ALTER TABLE t_sys_role_menu
    COMMENT '角色与菜单关联关系表';


CREATE TABLE IF NOT EXISTS t_sys_menu_permission
(
    menu_id       bigint COMMENT '关联菜单id',
    permission_id bigint COMMENT '关联权限id',
    PRIMARY KEY (menu_id, permission_id)
);

ALTER TABLE t_sys_menu_permission
    COMMENT '菜单与权限关联关系表';


CREATE TABLE IF NOT EXISTS t_sys_task
(
    id               bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             varchar(50)     NOT NULL COMMENT '任务名称',
    status           int             NOT NULL COMMENT '任务状态(1:等待中;2:执行中;3:任务被终止;4:已完成;5:执行失败)',
    type             int             NULL COMMENT '任务类型(1:导出任务)',
    percent          FLOAT           NOT NULL COMMENT '任务处理进度',
    message          varchar(255)    NULL COMMENT '任务信息(失败时记录失败原因)',
    start_time       datetime(3)        NULL COMMENT '任务开始时间',
    finish_time      datetime(3)        NULL COMMENT '任务完成时间',
    create_time      datetime(3)        NOT NULL COMMENT '创建时间',
    result           varchar(255)    NULL COMMENT '任务执行结果(不同的任务类型结果不同、记录导出任务这里存储的是导出文件在文件服务器的路径)',
    create_user_id   bigint COMMENT '创建人id',
    create_user_name varchar(50) COMMENT '创建人姓名',
    PRIMARY KEY (id)
);

ALTER TABLE t_sys_task
    COMMENT '系统任务处理表';