-- ============================================================================
-- V20261105__project_template_comment_material.sql
-- 三模块：项目模板 / @提及评论 / 材料库存管理
-- ============================================================================

-- ======================== 1. 项目模板 ========================

-- 项目模板主表：定义可复用的项目骨架（含默认字段、阶段、文档清单等配置）
CREATE TABLE IF NOT EXISTS `biz_project_template` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`            VARCHAR(50)  NOT NULL COMMENT '模板编号（唯一）',
    `name`            VARCHAR(100) NOT NULL COMMENT '模板名称',
    `category`        VARCHAR(50)  DEFAULT NULL COMMENT '模板分类（如 标准布线/机房改造/网络升级）',
    `description`     TEXT         DEFAULT NULL COMMENT '模板描述',
    `config_json`     TEXT         DEFAULT NULL COMMENT '模板配置JSON（默认字段值、阶段定义、文档清单、岗位职责等结构化配置）',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `created_by`      BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目模板';

-- ======================== 2. @提及评论 ========================

-- 评论表：支持项目/文档/任务等多业务对象的评论，comment_content 中 @用户名 触发通知
CREATE TABLE IF NOT EXISTS `biz_comment` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `biz_type`        VARCHAR(30)  NOT NULL COMMENT '业务类型：project/document/task',
    `biz_id`          BIGINT       NOT NULL COMMENT '业务对象ID',
    `content`         TEXT         NOT NULL COMMENT '评论内容（支持 @用户名 提及）',
    `mentions`        VARCHAR(500) DEFAULT NULL COMMENT '解析出的被提及用户ID列表（逗号分隔，冗余便于查询）',
    `author_id`       BIGINT       NOT NULL COMMENT '评论人ID',
    `author_name`     VARCHAR(50)  NOT NULL COMMENT '评论人姓名（冗余）',
    `parent_id`       BIGINT       DEFAULT NULL COMMENT '父评论ID（支持回复）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_comment_biz` (`biz_type`, `biz_id`),
    KEY `idx_comment_author` (`author_id`),
    KEY `idx_comment_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用评论';

-- ======================== 3. 材料库存管理 ========================

-- 材料主表：管理材料基础信息与当前库存
CREATE TABLE IF NOT EXISTS `biz_material` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`            VARCHAR(50)  NOT NULL COMMENT '材料编号（唯一）',
    `name`            VARCHAR(100) NOT NULL COMMENT '材料名称',
    `category`        VARCHAR(50)  DEFAULT NULL COMMENT '材料分类（如 光缆/网线/模块/配线架）',
    `spec`            VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
    `unit`            VARCHAR(20)  NOT NULL DEFAULT '个' COMMENT '计量单位（米/个/箱/卷）',
    `current_stock`   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '当前库存数量',
    `safety_stock`    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '安全库存（低于此值预警）',
    `unit_price`      DECIMAL(10,2) DEFAULT NULL COMMENT '单价（元）',
    `warehouse`       VARCHAR(50)  DEFAULT NULL COMMENT '所在仓库',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `remark`          VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_material_code` (`code`),
    KEY `idx_material_category` (`category`),
    KEY `idx_material_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料库存';

-- 库存变更记录表：记录每一次入库/出库/调整的流水
CREATE TABLE IF NOT EXISTS `biz_material_transaction` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `material_id`     BIGINT       NOT NULL COMMENT '材料ID',
    `transaction_type` VARCHAR(10) NOT NULL COMMENT '类型：in入库/out出库/adjust调整',
    `quantity`        DECIMAL(12,2) NOT NULL COMMENT '变更数量（正数；出库时从库存扣减）',
    `before_stock`    DECIMAL(12,2) NOT NULL COMMENT '变更前库存',
    `after_stock`     DECIMAL(12,2) NOT NULL COMMENT '变更后库存',
    `project_id`      BIGINT       DEFAULT NULL COMMENT '关联项目ID（出库时可指定）',
    `project_name`    VARCHAR(100) DEFAULT NULL COMMENT '关联项目名（冗余）',
    `operator_id`     BIGINT       NOT NULL COMMENT '操作人ID',
    `operator_name`   VARCHAR(50)  NOT NULL COMMENT '操作人姓名（冗余）',
    `remark`          VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_txn_material` (`material_id`),
    KEY `idx_txn_type` (`transaction_type`),
    KEY `idx_txn_project` (`project_id`),
    KEY `idx_txn_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变更记录';

-- 库存预警记录表：库存低于安全线时生成预警记录
CREATE TABLE IF NOT EXISTS `biz_material_alert` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `material_id`     BIGINT       NOT NULL COMMENT '材料ID',
    `material_name`   VARCHAR(100) NOT NULL COMMENT '材料名称（冗余）',
    `current_stock`   DECIMAL(12,2) NOT NULL COMMENT '触发时库存',
    `safety_stock`    DECIMAL(12,2) NOT NULL COMMENT '安全库存',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending待处理/resolved已处理',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `resolved_at`     DATETIME     DEFAULT NULL COMMENT '处理时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_material` (`material_id`),
    KEY `idx_alert_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警';
