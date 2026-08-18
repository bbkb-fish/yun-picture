create table if not exists yun_picture;

use yun_picture;
-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图片表
-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                      null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId)              -- 提升基于用户 ID 的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;

-- 图片审核功能的表
ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus INT DEFAULT 0 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    ADD COLUMN reviewMessage VARCHAR(512) NULL COMMENT '审核信息',
    ADD COLUMN reviewerId BIGINT NULL COMMENT '审核人 ID',
    ADD COLUMN reviewTime DATETIME NULL COMMENT '审核时间';

-- 创建基于 reviewStatus 列的索引
CREATE INDEX idx_reviewStatus ON picture (reviewStatus);

-- 原图
ALTER TABLE picture
    ADD COLUMN originUrl varchar(512) NULL COMMENT '原图地址';
-- 新增列
ALTER TABLE picture
    ADD COLUMN thumbnailUrl varchar(512) NULL comment '缩略图';

-- 空间表
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 null comment '空间图片的最大数量',
    totalSize  bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    -- 索引设计
    index idx_userId (userId),        -- 提升基于用户的查询效率
    index idx_spaceName (spaceName),  -- 提升基于空间名称的查询效率
    index idx_spaceLevel (spaceLevel) -- 提升按空间级别查询的效率
) comment '空间' collate = utf8mb4_unicode_ci;

-- 添加新列
ALTER TABLE picture
    ADD COLUMN spaceId bigint null comment '空间id';

-- 创建索引
CREATE INDEX idx_spaceId ON picture (spaceId);

ALTER TABLE  picture
        ADD COLUMN picColor varchar(16) null comment '图片颜色主色调';


-- 图片热度功能
create table picture_stat
(
    picture_id     bigint primary key,
    view_count     bigint default 0 not null,
    download_count bigint default 0 not null,
    like_count     bigint default 0 not null,
    favorite_count bigint default 0 not null,
    update_time    datetime default current_timestamp
        on update current_timestamp
);


-- 点赞功能
CREATE TABLE picture_like (
      id          BIGINT      NOT NULL,
      user_id     BIGINT      NOT NULL,
      picture_id  BIGINT      NOT NULL,
      create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uk_user_picture (user_id, picture_id),
      KEY idx_picture_id (picture_id),
      KEY idx_user_time (user_id, create_time)
);
-- 收藏功能
CREATE TABLE picture_favorite (
      id            BIGINT      NOT NULL,
      user_id       BIGINT      NOT NULL,
      picture_id    BIGINT      NOT NULL,
      favorite_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id),
      UNIQUE KEY uk_user_picture (user_id, picture_id),
      KEY idx_picture_id (picture_id),
      KEY idx_user_time (user_id, favorite_time)
);

-- 评论功能
CREATE TABLE picture_comment (
                                 id             BIGINT       NOT NULL COMMENT '评论 ID',
                                 picture_id     BIGINT       NOT NULL COMMENT '图片 ID',
                                 user_id        BIGINT       NOT NULL COMMENT '评论用户 ID',

                                 root_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '所属一级评论 ID',
                                 parent_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '直接回复的评论 ID',
                                 reply_user_id  BIGINT       NULL COMMENT '被回复用户 ID',

                                 content        VARCHAR(500) NOT NULL COMMENT '评论内容',
                                 reply_count    INT          NOT NULL DEFAULT 0 COMMENT '回复数量',

                                 create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
                                 is_delete      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除',

                                 PRIMARY KEY (id),
                                 KEY idx_picture_root_time (picture_id, root_id, create_time),
                                 KEY idx_root_time (root_id, create_time),
                                 KEY idx_user_time (user_id, create_time)
) COMMENT '图片评论';

-- 用户下载高清图片每日用量
CREATE TABLE user_download_daily (
                                     id BIGINT NOT NULL,
                                     user_id BIGINT NOT NULL,
                                     stat_date DATE NOT NULL,
                                     original_download_count INT NOT NULL DEFAULT 0,
                                     create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
                                     PRIMARY KEY (id),
                                     UNIQUE KEY uk_user_date (user_id, stat_date)
);

-- 用户通知：MySQL 保存离线消息，SSE 只负责在线实时推送
CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT NOT NULL COMMENT '通知 ID',
    user_id BIGINT NOT NULL COMMENT '接收用户 ID',
    type VARCHAR(32) NOT NULL COMMENT '通知类型',
    title VARCHAR(128) NOT NULL COMMENT '通知标题',
    content VARCHAR(500) NOT NULL COMMENT '通知内容',
    biz_type VARCHAR(32) NULL COMMENT '业务类型',
    biz_id BIGINT NULL COMMENT '业务对象 ID',
    dedupe_key VARCHAR(128) NULL COMMENT '消息幂等标识',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读',
    read_time DATETIME NULL COMMENT '读取时间',
    mq_status TINYINT NOT NULL DEFAULT 0 COMMENT 'MQ状态：0-待发送 1-已发送 2-发送中',
    mq_retry_count INT NOT NULL DEFAULT 0 COMMENT 'MQ发送重试次数',
    mq_next_retry_time DATETIME NULL COMMENT '下次MQ重试时间',
    mq_sent_time DATETIME NULL COMMENT 'RabbitMQ确认接收时间',
    mq_consumed_time DATETIME NULL COMMENT 'RabbitMQ消费者处理时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_dedupe (user_id, dedupe_key),
    KEY idx_user_read_time (user_id, is_read, create_time),
    KEY idx_user_time (user_id, create_time),
    KEY idx_mq_retry (mq_status, mq_next_retry_time)
) COMMENT '用户通知';


-- user_notification 引入MQ
ALTER TABLE user_notification
    ADD COLUMN mq_status TINYINT NOT NULL DEFAULT 0
        COMMENT 'MQ状态：0-待发送 1-已发送 2-发送中' AFTER read_time,
    ADD COLUMN mq_retry_count INT NOT NULL DEFAULT 0
        COMMENT 'MQ发送重试次数' AFTER mq_status,
    ADD COLUMN mq_next_retry_time DATETIME NULL
        COMMENT '下次MQ重试时间' AFTER mq_retry_count,
    ADD COLUMN mq_sent_time DATETIME NULL
        COMMENT 'RabbitMQ确认接收时间' AFTER mq_next_retry_time,
    ADD COLUMN mq_consumed_time DATETIME NULL
        COMMENT 'RabbitMQ消费者处理时间' AFTER mq_sent_time,
    ADD KEY idx_mq_retry (mq_status, mq_next_retry_time);
UPDATE user_notification
SET mq_status = 1,
    mq_sent_time = create_time,
    mq_consumed_time = create_time
WHERE mq_status = 0;

