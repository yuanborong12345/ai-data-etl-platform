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

-- 业务模板表：用于存储用户在前端设计的模板列结构
CREATE TABLE IF NOT EXISTS template_info
(
    id             BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    templateName   VARCHAR(128)                           NOT NULL COMMENT '模板名称',
    description    VARCHAR(512)                           NULL COMMENT '模板描述及使用说明',
    templateSchema TEXT                                   NOT NULL COMMENT '模板列结构定义（JSON字符串，包含列名、key、类型、是否必填）',
    creatorId      BIGINT                                 NOT NULL COMMENT '创建人用户ID',
    auditStatus    TINYINT      DEFAULT 0                 NOT NULL COMMENT '审核状态：0待审核 1审核通过 2审核驳回',
    auditMsg       VARCHAR(512)                           NULL COMMENT '审核驳回原因',
    createTime     DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime     DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete       TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除：0否 1是',
    INDEX idx_creatorId (creatorId),
    INDEX idx_auditStatus (auditStatus)
) COMMENT '业务模板表' COLLATE = utf8mb4_unicode_ci;

-- 文件元数据表：记录每个上传文件的信息与处理状态
create table if not exists file_info
(
    id           bigint auto_increment comment '主键ID' primary key,
    fileName     varchar(256)                           not null comment '原始文件名',
    fileSize     bigint                                 null comment '文件大小（字节）',
    fileType     varchar(32)                            null comment '文件类型（扩展名）',
    storagePath  varchar(512)                           null comment '存储路径',
    userId       bigint                                 not null comment '上传用户ID',
    templateId   bigint                                 not null comment '关联的模板ID',
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
