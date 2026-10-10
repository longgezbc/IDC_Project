-- =============================================================================
-- 实际时间字段 + 文档审核权限 + PM用户查询权限 迁移脚本
-- 功能：
--   1. biz_project 表增加 actual_start_date 列（实际开工日期）
--   2. biz_task_progress_log 表增加 actual_start_date / actual_end_date 列
--   3. sys_permission 增加 document:review 权限记录
--   4. sys_role_permission 为 admin(1) 和 project_manager(2) 分配 document:review 权限
--   5. sys_role_permission 为 project_manager(2) 分配 system:user:query 权限
--      （修复：PM 创建项目时需选择项目经理，需查看用户列表）
-- 作者：hbsz
-- 日期：2026-07-14
-- 说明：
--   1. 脚本需手动执行；DDL 做幂等处理，可重复执行。
--   2. 通过 information_schema 判断列/记录是否存在后再执行，避免重复执行报错。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. biz_project 增加 actual_start_date 列（实际开工日期）
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project'
      AND COLUMN_NAME = 'actual_start_date');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_project` ADD COLUMN `actual_start_date` DATE DEFAULT NULL COMMENT ''实际开工日期'' AFTER `end_date`',
    'SELECT ''biz_project.actual_start_date already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. biz_task_progress_log 增加 actual_start_date 列（实际开工日期）
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_task_progress_log'
      AND COLUMN_NAME = 'actual_start_date');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_task_progress_log` ADD COLUMN `actual_start_date` DATE DEFAULT NULL COMMENT ''实际开工日期'' AFTER `images`',
    'SELECT ''biz_task_progress_log.actual_start_date already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3. biz_task_progress_log 增加 actual_end_date 列（实际结束日期）
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_task_progress_log'
      AND COLUMN_NAME = 'actual_end_date');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_task_progress_log` ADD COLUMN `actual_end_date` DATE DEFAULT NULL COMMENT ''实际结束日期'' AFTER `actual_start_date`',
    'SELECT ''biz_task_progress_log.actual_end_date already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 4. sys_permission 增加 document:review 权限记录
--    修复：原脚本使用错误列名（name/code/type/is_menu/description），
--    实际表结构为 permission_name/permission_code/permission_type/is_visible/remark，
--    在真实库执行会报 Unknown column 错误导致迁移失败。
--    同时取消硬编码 id=81 / parent_id=36，改用 permission_code 子查询动态获取，
--    与 V20260705 脚本风格一致，避免环境差异导致主键冲突或错配。
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM `sys_permission` WHERE `permission_code` = 'document' LIMIT 1) AS t1), 0),
  '文档审核', 'document:review', 2, '', NULL, '', 6, 0, 1, '审核上传的文档（通过/驳回）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'document:review') AS t2);

-- -----------------------------------------------------------------------------
-- 5. 为 admin 角色分配 document:review 权限
--    修复：role_id / permission_id 均通过 role_code / permission_code 子查询动态获取
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` = 'document:review'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.id AND rp.`permission_id` = p.id) AS t3
  );

-- -----------------------------------------------------------------------------
-- 6. 为 project_manager 角色分配 document:review 权限
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` = 'document:review'
WHERE r.`role_code` = 'project_manager'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.id AND rp.`permission_id` = p.id) AS t4
  );

-- -----------------------------------------------------------------------------
-- 7. 为 project_manager(2) 角色分配 system:user:query 权限（权限ID=11）
--    修复：PM 创建项目时需选择项目经理，需查看用户列表
-- -----------------------------------------------------------------------------
SET @rp_exists = (SELECT COUNT(*) FROM `sys_role_permission` WHERE `role_id` = 2 AND `permission_id` = 11);

SET @sql = IF(@rp_exists = 0,
    'INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (2, 11)',
    'SELECT ''role_permission pm->system:user:query already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
