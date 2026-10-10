-- =============================================================================
-- 组织架构升级数据库迁移脚本（阶段1：岗位职级体系）
-- 功能：岗位职级体系 + 用户岗位关联 + 项目授权申请/项目组协作 + 权限点扩展
-- 作者：hbsz
-- 日期：2026-07-05
-- 说明：
--   1. 本项目未引入 Flyway，脚本需手动执行；所有 DDL 均做幂等处理，可重复执行。
--   2. ALTER TABLE 通过 information_schema 判断列/索引是否存在后再执行，避免重复执行报错。
--   3. INSERT 数据均带 WHERE NOT EXISTS 守卫，防止重复插入。
--   4. sys_permission 表实际字段为 permission_code/permission_name/permission_type
--      （非 code/name/type），sys_role 使用 role_code，本脚本已对齐真实表结构。
--   5. parent_id / role_id 均通过子查询动态获取，不硬编码 ID，避免环境差异导致错配。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- A1. 岗位职级表 sys_position
--     定义 6 个标准岗位（pm/senior_engineer/engineer/junior_engineer/outsourcing/viewer），
--     level 越小职级越高（1 最高，9 最低），用于精细化的功能权限与数据权限控制。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_position` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code`        VARCHAR(50)  NOT NULL COMMENT '岗位编码（如 pm/senior_engineer/engineer/junior_engineer/outsourcing/viewer）',
  `name`        VARCHAR(50)  NOT NULL COMMENT '岗位名称',
  `level`       INT          NOT NULL COMMENT '职级（1最高，9最低）',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '岗位描述',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位职级表';

-- -----------------------------------------------------------------------------
-- A2. sys_user 扩展 position_id / dept_id 字段
--     position_id 关联 sys_position.id，实现用户与岗位的绑定；
--     dept_id 为预留字段，当前不启用部门隔离，供后续部门体系扩展使用。
--     （注：sys_user 已有 department_id（旧字段）与 position（文本职位），
--      此处新增 position_id（外键岗位）与 dept_id（预留部门），与旧字段并存不冲突）
-- -----------------------------------------------------------------------------
-- 2.1 新增 position_id 列
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'position_id');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `sys_user` ADD COLUMN `position_id` BIGINT NULL COMMENT ''岗位ID（关联 sys_position.id）''',
               'SELECT ''[SKIP] sys_user.position_id 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 新增 dept_id 列（预留）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'dept_id');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `sys_user` ADD COLUMN `dept_id` BIGINT NULL COMMENT ''部门ID（预留字段，当前不启用部门隔离）''',
               'SELECT ''[SKIP] sys_user.dept_id 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.3 新增 position_id 索引
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'idx_position_id');
SET @ddl := IF(@idx_exists = 0,
               'ALTER TABLE `sys_user` ADD INDEX `idx_position_id` (`position_id`)',
               'SELECT ''[SKIP] 索引 idx_position_id 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.4 新增 dept_id 索引
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'idx_dept_id');
SET @ddl := IF(@idx_exists = 0,
               'ALTER TABLE `sys_user` ADD INDEX `idx_dept_id` (`dept_id`)',
               'SELECT ''[SKIP] 索引 idx_dept_id 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- A3. 初始化岗位数据（6 个标准岗位）
--     仅在岗位不存在时插入，保证幂等。
-- -----------------------------------------------------------------------------
INSERT INTO `sys_position`(`code`, `name`, `level`, `description`)
SELECT 'pm', '项目经理', 2, '项目负责人，可创建/转移/审核项目'
WHERE NOT EXISTS (SELECT 1 FROM sys_position WHERE code = 'pm');

INSERT INTO `sys_position`(`code`, `name`, `level`, `description`)
SELECT 'senior_engineer', '高级工程师', 3, '资深工程师，可审核进度、删除文档'
WHERE NOT EXISTS (SELECT 1 FROM sys_position WHERE code = 'senior_engineer');

INSERT INTO `sys_position`(`code`, `name`, `level`, `description`)
SELECT 'engineer', '工程师', 4, '普通工程师，可填报进度、上传文档'
WHERE NOT EXISTS (SELECT 1 FROM sys_position WHERE code = 'engineer');

INSERT INTO `sys_position`(`code`, `name`, `level`, `description`)
SELECT 'junior_engineer', '初级工程师', 5, '实习生/新人，不能删除文档'
WHERE NOT EXISTS (SELECT 1 FROM sys_position WHERE code = 'junior_engineer');

INSERT INTO `sys_position`(`code`, `name`, `level`, `description`)
SELECT 'outsourcing', '外包人员', 6, '外包人员，仅查看，不能下载/导出'
WHERE NOT EXISTS (SELECT 1 FROM sys_position WHERE code = 'outsourcing');

INSERT INTO `sys_position`(`code`, `name`, `level`, `description`)
SELECT 'viewer', '只读用户', 9, '客户/审计，仅查看'
WHERE NOT EXISTS (SELECT 1 FROM sys_position WHERE code = 'viewer');

-- -----------------------------------------------------------------------------
-- A4. 回填现有用户的 position_id（基于 role_code 推断）
--     admin → 不设置 position_id（管理员不参与岗位体系）
--     project_manager → pm
--     engineer        → engineer
--     user            → viewer
--     仅回填 position_id 为空的用户，可重复执行。
-- -----------------------------------------------------------------------------
UPDATE `sys_user` u
JOIN `sys_user_role` ur ON u.id = ur.user_id
JOIN `sys_role` r ON ur.role_id = r.id
SET u.position_id = (
  SELECT id FROM sys_position WHERE code =
    CASE r.role_code
      WHEN 'project_manager' THEN 'pm'
      WHEN 'engineer' THEN 'engineer'
      WHEN 'user' THEN 'viewer'
      ELSE NULL
    END
)
WHERE u.position_id IS NULL
  AND r.role_code IN ('project_manager', 'engineer', 'user');

-- -----------------------------------------------------------------------------
-- A5. biz_project_assignment 扩展授权有效期与范围字段
--     permission_scope：授权范围（read_only/read_write/review）
--     expire_at：授权过期时间（NULL=永久）
--     revoke_reason / revoked_at：撤销原因与时间，便于审计
-- -----------------------------------------------------------------------------
-- 5.1 permission_scope
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_project_assignment' AND column_name = 'permission_scope');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `biz_project_assignment` ADD COLUMN `permission_scope` VARCHAR(20) NOT NULL DEFAULT ''read_write'' COMMENT ''授权范围：read_only只读/read_write读写/review可审核''',
               'SELECT ''[SKIP] biz_project_assignment.permission_scope 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.2 expire_at
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_project_assignment' AND column_name = 'expire_at');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `biz_project_assignment` ADD COLUMN `expire_at` DATETIME NULL COMMENT ''授权过期时间（NULL=永久）''',
               'SELECT ''[SKIP] biz_project_assignment.expire_at 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.3 revoke_reason
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_project_assignment' AND column_name = 'revoke_reason');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `biz_project_assignment` ADD COLUMN `revoke_reason` VARCHAR(200) NULL COMMENT ''撤销原因''',
               'SELECT ''[SKIP] biz_project_assignment.revoke_reason 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.4 revoked_at
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_project_assignment' AND column_name = 'revoked_at');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `biz_project_assignment` ADD COLUMN `revoked_at` DATETIME NULL COMMENT ''实际撤销时间''',
               'SELECT ''[SKIP] biz_project_assignment.revoked_at 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- A6. 项目授权申请表 biz_project_auth_request
--     工程师/PM 向他人申请项目授权的审批流转表，记录申请、审批全流程。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_project_auth_request` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`          BIGINT       NOT NULL COMMENT '项目ID',
  `applicant_id`        BIGINT       NOT NULL COMMENT '申请人用户ID',
  `target_engineer_id`  BIGINT       NOT NULL COMMENT '目标工程师用户ID',
  `scope`               VARCHAR(20)  NOT NULL DEFAULT 'read_write' COMMENT '申请范围：read_only/read_write/review',
  `expire_at`           DATETIME     DEFAULT NULL COMMENT '申请有效期',
  `reason`              VARCHAR(500) DEFAULT NULL COMMENT '申请理由',
  `status`              VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/rejected',
  `approver_id`         BIGINT       DEFAULT NULL COMMENT '审批人ID',
  `approved_at`         DATETIME     DEFAULT NULL COMMENT '审批时间',
  `comment`             VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_applicant` (`applicant_id`),
  KEY `idx_target` (`target_engineer_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目授权申请表';

-- -----------------------------------------------------------------------------
-- A7. 项目组成员表 biz_project_team（跨部门协作）
--     一个项目可组建团队，成员包含 leader/member/observer 三种角色，
--     用于项目可见范围 team_scope=team 时的数据权限控制。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_project_team` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`    BIGINT       NOT NULL COMMENT '项目ID',
  `user_id`       BIGINT       NOT NULL COMMENT '成员用户ID',
  `role_in_team`  VARCHAR(20)  NOT NULL DEFAULT 'member' COMMENT '组内角色：leader/member/observer',
  `joined_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `left_at`       DATETIME     DEFAULT NULL COMMENT '离组时间',
  `is_active`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否在组：1是 0否',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user_active` (`project_id`, `user_id`, `is_active`),
  KEY `idx_user` (`user_id`, `is_active`),
  KEY `idx_project` (`project_id`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目组成员表';

-- -----------------------------------------------------------------------------
-- A8. 项目表 biz_project 新增 team_scope 字段
--     控制项目可见范围：assignment（仅授权可见）/ team（项目组内可见）/ public（全公司可见）
-- -----------------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_project' AND column_name = 'team_scope');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE `biz_project` ADD COLUMN `team_scope` VARCHAR(20) NOT NULL DEFAULT ''assignment'' COMMENT ''项目可见范围：assignment=仅授权可见/team=项目组内可见/public=全公司可见''',
               'SELECT ''[SKIP] biz_project.team_scope 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- A9. 新增权限点（岗位管理 + 项目授权申请/审批 + 项目组管理）
--     parent_id 动态查询：
--       - position:* 系列挂在 permission_code='system'（系统管理菜单）下；
--       - project:auth:* / project:team:manage 系列挂在 permission_code='project'（项目管理菜单）下。
--     若父菜单不存在则 parent_id 取 0（顶级），保证不会因环境差异导致插入失败。
--     permission_type：1=菜单 2=按钮（与现有 SysPermission 实体定义一致）
-- -----------------------------------------------------------------------------
-- 9.1 position:list（菜单）
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'system' LIMIT 1) AS t1), 0),
  'position:list', '岗位列表', 1, '/system/positions', 'system/PositionList', 'Postcard', 5, 1, 1, '岗位管理-列表'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'position:list');

-- 9.2 position:query（按钮）
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'position:list' LIMIT 1) AS t1), 0),
  'position:query', '岗位查询', 2, NULL, NULL, NULL, 6, 0, 1, '岗位管理-查询'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'position:query');

-- 9.3 position:add
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'position:list' LIMIT 1) AS t1), 0),
  'position:add', '新增岗位', 2, NULL, NULL, NULL, 7, 0, 1, '岗位管理-新增'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'position:add');

-- 9.4 position:edit
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'position:list' LIMIT 1) AS t1), 0),
  'position:edit', '编辑岗位', 2, NULL, NULL, NULL, 8, 0, 1, '岗位管理-编辑'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'position:edit');

-- 9.5 position:remove
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'position:list' LIMIT 1) AS t1), 0),
  'position:remove', '删除岗位', 2, NULL, NULL, NULL, 9, 0, 1, '岗位管理-删除'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'position:remove');

-- 9.6 project:auth:request
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'project' LIMIT 1) AS t1), 0),
  'project:auth:request', '申请项目授权', 2, NULL, NULL, NULL, 10, 0, 1, '项目授权-申请'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'project:auth:request');

-- 9.7 project:auth:approve
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'project' LIMIT 1) AS t1), 0),
  'project:auth:approve', '审批项目授权', 2, NULL, NULL, NULL, 11, 0, 1, '项目授权-审批'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'project:auth:approve');

-- 9.8 project:team:manage
INSERT INTO `sys_permission`(`parent_id`, `permission_code`, `permission_name`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'project' LIMIT 1) AS t1), 0),
  'project:team:manage', '管理项目组', 2, NULL, NULL, NULL, 12, 0, 1, '项目组-管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'project:team:manage');

-- -----------------------------------------------------------------------------
-- A10. 角色授权：给 admin 授予新增权限 + 给 project_manager 授予项目相关权限
--      admin 角色自动拥有全部权限（运行期 ALL_PERMISSION 放行），此处仍写入 DB 便于前端菜单展示。
--      通过 role_code 子查询定位角色，避免硬编码 role_id。
-- -----------------------------------------------------------------------------
-- 10.1 给 admin 角色授予岗位管理全部权限
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'admin'
  AND p.permission_code IN ('position:list','position:query','position:add','position:edit','position:remove',
                            'project:auth:request','project:auth:approve','project:team:manage')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 10.2 给 project_manager 角色授予项目授权申请/审批 + 项目组管理权限
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code = 'project_manager'
  AND p.permission_code IN ('project:auth:request','project:auth:approve','project:team:manage')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- -----------------------------------------------------------------------------
-- A11. 统计与验证（执行后人工核对各阶段数据是否符合预期）
-- -----------------------------------------------------------------------------
SELECT '岗位初始化' AS stage, COUNT(*) AS cnt FROM sys_position;
SELECT '用户岗位回填' AS stage, COUNT(*) AS filled FROM sys_user WHERE position_id IS NOT NULL;
SELECT '项目授权申请表' AS stage, COUNT(*) AS cnt FROM biz_project_auth_request;
SELECT '项目组表' AS stage, COUNT(*) AS cnt FROM biz_project_team;
SELECT '新增权限点' AS stage, COUNT(*) AS cnt FROM sys_permission
  WHERE permission_code IN ('position:list','position:query','position:add','position:edit','position:remove',
                             'project:auth:request','project:auth:approve','project:team:manage');

-- =============================================================================
-- 迁移完成
-- =============================================================================
