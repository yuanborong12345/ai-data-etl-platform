-- =============================================
-- ai-data-user 模块
-- =============================================
create database if not exists ai_data_etl_db
    default character set utf8mb4 collate utf8mb4_unicode_ci;

use ai_data_etl_db;

create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(64)                           not null comment '用户账号',
    userPassword varchar(128)                           not null comment '用户密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    status       tinyint      default 0                 not null comment '帐号状态（0正常 1停用)',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_account_delete (userAccount, isDelete),
    INDEX idx_userName (userName)
) comment '用户表' collate = utf8mb4_unicode_ci;

-- =============================================
-- ai-data-processor 模块
-- =============================================

-- 文件元数据表：记录每个上传文件的信息与处理状态
create table if not exists file_info
(
    id           bigint auto_increment comment '主键ID' primary key,
    fileName     varchar(256)                           not null comment '原始文件名',
    fileSize     bigint                                 null comment '文件大小（字节）',
    fileType     varchar(32)                            null comment '文件类型（扩展名）',
    storagePath  varchar(512)                           null comment '存储路径',
    userId       bigint                                 not null comment '上传用户ID',
    taskId       varchar(64)                            not null comment '任务ID（UUID）',
    status       tinyint      default 0                 not null comment '文件状态：0上传中 1待解析 2解析中 3AI分析中 4完成 5失败',
    promptContent text                                   null comment '用户输入的分析需求描述（如：分析各区域季度销售趋势）',
    errorMsg     varchar(1024)                          null comment '失败原因',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    INDEX idx_userId (userId),
    INDEX idx_taskId (taskId),
    INDEX idx_status (status)
) comment '文件元数据表' collate = utf8mb4_unicode_ci;
