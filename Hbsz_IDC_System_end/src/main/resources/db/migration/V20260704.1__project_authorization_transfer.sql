-- =============================================================================
-- 项目授权与转移审计数据库迁移脚本
-- 功能：实现基于角色的项目可见性控制 + PM 转移项目归属工程师 + 完整审计日志
-- 作者：hbsz
-- 日期：2026-07-04
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 项目授权表：记录工程师对项目的显式授权关系（多对多）
-- -----------------------------------------------------------------------------
-- 注：使用 CREATE TABLE IF NOT EXISTS 而非 DROP TABLE + CREATE TABLE，
--     避免在已存在数据的数据库上执行迁移时丢失用户授权关系数据。
--     全新数据库：表结构由 Docker init 的 01-schema.sql 创建（已是最新状态，含后续字段）；
--     已存在数据库：保留原表结构与数据，仅执行后续 INSERT 回填（均为幂等）。
CREATE TABLE IF NOT EXISTS `biz_project_assignment` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`     BIGINT      NOT NULL COMMENT '项目ID（FK→biz_project.id）',
  `engineer_id`    BIGINT      NOT NULL COMMENT '工程师用户ID（FK→sys_user.id）',
  `assigned_by`    BIGINT      NOT NULL COMMENT '授权人（PM）用户ID（FK→sys_user.id）',
  `assigned_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  `is_active`      TINYINT     NOT NULL DEFAULT 1 COMMENT '是否有效：1有效 / 0已撤销',
  `remark`         VARCHAR(500) DEFAULT NULL COMMENT '授权备注',
  PRIMARY KEY (`id`),
  -- 不在 (project_id, engineer_id, is_active) 上加 UNIQUE 约束：
  -- 软删除模式（is_active=0）下，同一对 project+engineer 可能出现多条历史撤销记录，
  -- UNIQUE 约束会导致 revoke 后再次 assign 再 revoke 时发生 Duplicate entry 错误。
  -- 同一 project+engineer 的"有效授权唯一性"由应用层在 assignEngineer 中通过
  -- countActiveAssignment 检查保证，无需 DB 层强制。
  KEY `idx_engineer_active` (`engineer_id`, `is_active`),
  KEY `idx_project_active` (`project_id`, `is_active`),
  KEY `idx_assigned_by` (`assigned_by`),
  CONSTRAINT `fk_pa_project`  FOREIGN KEY (`project_id`)  REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pa_engineer` FOREIGN KEY (`engineer_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pa_assigner` FOREIGN KEY (`assigned_by`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目-工程师授权关系表';

-- -----------------------------------------------------------------------------
-- 2. 项目转移审计日志表：记录每次 assign / revoke / transfer 操作
-- -----------------------------------------------------------------------------
-- 同上：使用 IF NOT EXISTS 避免破坏已有审计日志数据
CREATE TABLE IF NOT EXISTS `biz_project_transfer_log` (
  `id`                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`           BIGINT      NOT NULL COMMENT '项目ID',
  `project_name`         VARCHAR(200) DEFAULT NULL COMMENT '项目名称（冗余，便于审计展示）',
  `action_type`          VARCHAR(20) NOT NULL COMMENT '操作类型：assign（授权）/ revoke（撤销）/ transfer（转移）',
  `from_engineer_id`     BIGINT      DEFAULT NULL COMMENT '原工程师ID（assign 时为 NULL）',
  `from_engineer_name`   VARCHAR(50) DEFAULT NULL COMMENT '原工程师姓名（冗余）',
  `to_engineer_id`       BIGINT      DEFAULT NULL COMMENT '目标工程师ID（revoke 时为 NULL）',
  `to_engineer_name`     VARCHAR(50) DEFAULT NULL COMMENT '目标工程师姓名（冗余）',
  `operated_by`          BIGINT      NOT NULL COMMENT '操作人（PM）ID',
  `operator_name`        VARCHAR(50) NOT NULL COMMENT '操作人姓名（冗余）',
  `operated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `remark`               VARCHAR(500) DEFAULT NULL COMMENT '操作备注',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_operated_by` (`operated_by`),
  KEY `idx_operated_at` (`operated_at`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_from_engineer` (`from_engineer_id`),
  KEY `idx_to_engineer` (`to_engineer_id`),
  CONSTRAINT `fk_ptl_project` FOREIGN KEY (`project_id`) REFERENCES `biz_project` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ptl_operator` FOREIGN KEY (`operated_by`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目转移/授权操作审计日志表';

-- -----------------------------------------------------------------------------
-- 3. 新增权限码：project:transfer（PM 转移项目授权）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`
  (`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'project' LIMIT 1) AS tmp),
  '项目授权转移', 'project:transfer', 2, '/projects/transfer-logs', 'projects/TransferLogs', 'Share', 65, 1, 1, '项目经理将项目授权给指定工程师'
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'project:transfer');

-- -----------------------------------------------------------------------------
-- 4. 将 project:transfer 权限授予 project_manager 角色（admin 自动拥有全部权限）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'project_manager' AND p.permission_code = 'project:transfer'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- -----------------------------------------------------------------------------
-- 5. 历史数据回填：根据现有 biz_task.assignee_id 关系，为工程师自动授权其参与的项目
--    确保 PM 转移功能上线后，原有工程师任务进度填报流程不受影响
-- -----------------------------------------------------------------------------
INSERT INTO `biz_project_assignment` (`project_id`, `engineer_id`, `assigned_by`, `assigned_at`, `is_active`, `remark`)
SELECT DISTINCT t.project_id, t.assignee_id, 1, NOW(), 1, '系统回填：基于历史任务负责人关系自动授权'
FROM biz_task t
WHERE t.assignee_id IS NOT NULL
  AND t.project_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM biz_project_assignment pa
    WHERE pa.project_id = t.project_id AND pa.engineer_id = t.assignee_id
  );

-- 同时为项目经理（biz_project.manager_id）回填授权（PM 自己也可查看其负责的项目）
INSERT INTO `biz_project_assignment` (`project_id`, `engineer_id`, `assigned_by`, `assigned_at`, `is_active`, `remark`)
SELECT DISTINCT p.id, p.manager_id, 1, NOW(), 1, '系统回填：基于历史 manager_id 自动授权'
FROM biz_project p
WHERE p.manager_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM biz_project_assignment pa
    WHERE pa.project_id = p.id AND pa.engineer_id = p.manager_id
  );

-- =============================================================================
-- 迁移完成
-- =============================================================================
