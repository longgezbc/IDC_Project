-- =============================================================================
-- 名称唯一约束 + document:review 权限数据修复 迁移脚本
-- 功能：
--   1. sys_role.role_name 添加唯一约束（角色名不允许重复）
--   2. biz_customer.name 添加唯一约束（客户名不允许重复）
--   3. biz_project.name 添加唯一约束（项目名不允许重复）
--   4. 补齐 document:review 权限及其角色授权（数据修复，幂等）
--      背景：V20260714 原版本使用错误列名导致该权限 INSERT 无法在真实库执行，
--      本节保证已应用过 V20260714（Flyway 记录已存在、修正后的语句不会重跑）
--      的环境也能补齐数据。
-- 作者：hbsz
-- 日期：2026-08-15
-- 说明：
--   1. DDL 均通过 information_schema 做存在性检查（幂等，可重复执行）。
--   2. 唯一约束仅在“索引不存在 且 目标列无重复值”时创建：
--      存量数据已有重名的库自动跳过（SELECT 提示），不会因迁移失败阻塞启动；
--      此类库由服务层重名校验（Role/Customer/Project Service）阻止新增重复。
--   3. 不使用 USE 语句（数据源 URL 已指定数据库）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. sys_role.role_name 唯一约束
-- -----------------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_role'
      AND INDEX_NAME = 'uk_role_name');

SET @dup_exists = (SELECT COUNT(*) FROM
    (SELECT `role_name` FROM `sys_role` GROUP BY `role_name` HAVING COUNT(*) > 1 LIMIT 1) AS t);

SET @sql = IF(@idx_exists = 0 AND @dup_exists = 0,
    'ALTER TABLE `sys_role` ADD UNIQUE KEY `uk_role_name` (`role_name`)',
    'SELECT ''skip: sys_role uk_role_name already exists or duplicate role_name present'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. biz_customer.name 唯一约束
-- -----------------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_customer'
      AND INDEX_NAME = 'uk_customer_name');

SET @dup_exists = (SELECT COUNT(*) FROM
    (SELECT `name` FROM `biz_customer` GROUP BY `name` HAVING COUNT(*) > 1 LIMIT 1) AS t);

SET @sql = IF(@idx_exists = 0 AND @dup_exists = 0,
    'ALTER TABLE `biz_customer` ADD UNIQUE KEY `uk_customer_name` (`name`)',
    'SELECT ''skip: biz_customer uk_customer_name already exists or duplicate name present'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3. biz_project.name 唯一约束
-- -----------------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project'
      AND INDEX_NAME = 'uk_project_name');

SET @dup_exists = (SELECT COUNT(*) FROM
    (SELECT `name` FROM `biz_project` GROUP BY `name` HAVING COUNT(*) > 1 LIMIT 1) AS t);

SET @sql = IF(@idx_exists = 0 AND @dup_exists = 0,
    'ALTER TABLE `biz_project` ADD UNIQUE KEY `uk_project_name` (`name`)',
    'SELECT ''skip: biz_project uk_project_name already exists or duplicate name present'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 4. 补齐 document:review 权限（幂等，按 permission_code 判断）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT
  IFNULL((SELECT id FROM (SELECT id FROM `sys_permission` WHERE `permission_code` = 'document' LIMIT 1) AS t1), 0),
  '文档审核', 'document:review', 2, '', NULL, '', 6, 0, 1, '审核上传的文档（通过/驳回）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'document:review') AS t2);

-- 4.1 为 admin 角色分配 document:review 权限
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` = 'document:review'
WHERE r.`role_code` = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.id AND rp.`permission_id` = p.id) AS t3
  );

-- 4.2 为 project_manager 角色分配 document:review 权限
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` = 'document:review'
WHERE r.`role_code` = 'project_manager'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.id AND rp.`permission_id` = p.id) AS t4
  );
