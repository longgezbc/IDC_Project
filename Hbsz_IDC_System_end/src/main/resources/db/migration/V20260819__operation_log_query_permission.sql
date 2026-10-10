-- =============================================================================
-- V20260819: 操作日志查询功能
--   1. system:log:query 权限（挂在系统管理菜单下，仅 admin 绑定）；
--   2. created_at 单列索引（审计列表默认按时间倒序，全量无筛选场景走此索引）。
-- 幂等性：全部通过 information_schema 存在性检查 + NOT EXISTS 防重。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. system:log:query 菜单权限，挂在系统管理菜单（system:user:query 的父节点）下
-- -----------------------------------------------------------------------------
SET @sys_parent := (SELECT `id` FROM (SELECT `id` FROM `sys_permission` WHERE `permission_code` = 'system:user:query' LIMIT 1) AS t);

INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT @sys_parent, '操作日志', 'system:log:query', 1, 'system/logs', 'system/OperationLog', 'Document', 5, 1, 1, '操作日志审计查询（分页检索），仅管理员'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'system:log:query') AS t1);

-- -----------------------------------------------------------------------------
-- 2. 角色绑定：admin 获得日志查询权限（审计数据敏感，不给项目经理）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` = 'system:log:query'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`) AS t2
  );

-- -----------------------------------------------------------------------------
-- 3. created_at 单列索引（时间倒序分页；已有 idx_user_created 覆盖按人+时间场景）
-- -----------------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_operation_log'
      AND INDEX_NAME = 'idx_log_created');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `sys_operation_log` ADD INDEX `idx_log_created` (`created_at`)',
    'SELECT ''skip: sys_operation_log idx_log_created already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
