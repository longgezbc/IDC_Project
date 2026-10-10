-- ============================================================================
-- 迁移脚本：移除工作量相关字段与字典数据
-- ============================================================================
-- 背景：工作量功能在评估后被认定不需要，需要回滚相关 DDL 与字典数据。
--       历史上曾以 V20260715_2__add_project_total_workload.sql 手动执行过，
--       本脚本作为其逆操作，移除工作量字段与字典。
--
-- 影响范围：
--   - biz_project: DROP total_workload, workload_unit
--   - biz_task: DROP planned_workload, actual_workload, workload_unit
--   - biz_task_progress_log: DROP workload_description, workload_hours
--   - v_pending_progress_review: 重建视图（移除工作量字段引用）
--   - sys_dict_data / sys_dict_type: 删除 workload_unit 相关字典
--
-- 特性：
--   - 幂等：所有 DROP COLUMN 均做 information_schema 存在性检查，可重复执行
--   - 视图使用 DROP IF EXISTS + CREATE，重建幂等
--   - 字典数据 DELETE 无副作用（不存在则 0 行受影响）
--
-- 注：Flyway 执行时已通过 datasource URL 指定数据库，无需 USE 语句
-- ============================================================================
-- 作者：hbsz  日期：2026-07-15
-- ============================================================================

-- ============================================================================
-- 一、删除 biz_project 表的工作量字段
-- ============================================================================
-- 1. 删除 total_workload 列（项目总工作量基准）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project' AND COLUMN_NAME = 'total_workload');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `biz_project` DROP COLUMN `total_workload`',
    'SELECT ''biz_project.total_workload already removed'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 删除 workload_unit 列（工作量单位）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project' AND COLUMN_NAME = 'workload_unit');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `biz_project` DROP COLUMN `workload_unit`',
    'SELECT ''biz_project.workload_unit already removed'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 二、删除 biz_task 表的工作量字段
-- ============================================================================
-- 3. 删除 planned_workload 列（计划工作量）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_task' AND COLUMN_NAME = 'planned_workload');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `biz_task` DROP COLUMN `planned_workload`',
    'SELECT ''biz_task.planned_workload already removed'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 删除 actual_workload 列（实际已完成工作量）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_task' AND COLUMN_NAME = 'actual_workload');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `biz_task` DROP COLUMN `actual_workload`',
    'SELECT ''biz_task.actual_workload already removed'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. 删除 workload_unit 列（工作量单位）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_task' AND COLUMN_NAME = 'workload_unit');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `biz_task` DROP COLUMN `workload_unit`',
    'SELECT ''biz_task.workload_unit already removed'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 三、删除 biz_task_progress_log 表的工作量字段
-- ============================================================================
-- 6. 删除 workload_description 列（工作量描述）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_task_progress_log' AND COLUMN_NAME = 'workload_description');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `biz_task_progress_log` DROP COLUMN `workload_description`',
    'SELECT ''biz_task_progress_log.workload_description already removed'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. 删除 workload_hours 列（工时）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_task_progress_log' AND COLUMN_NAME = 'workload_hours');
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `biz_task_progress_log` DROP COLUMN `workload_hours`',
    'SELECT ''biz_task_progress_log.workload_hours already removed'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 四、重建视图 v_pending_progress_review（移除工作量字段引用）
-- ============================================================================
DROP VIEW IF EXISTS `v_pending_progress_review`;
CREATE VIEW `v_pending_progress_review` AS
SELECT
    `l`.`id` AS `log_id`,
    `l`.`task_id` AS `task_id`,
    `l`.`project_id` AS `project_id`,
    `l`.`progress` AS `progress`,
    `l`.`previous_progress` AS `previous_progress`,
    `l`.`remark` AS `remark`,
    `l`.`images` AS `images`,
    `l`.`report_time` AS `report_time`,
    `t`.`name` AS `task_name`,
    `t`.`status` AS `task_status`,
    `t`.`progress` AS `task_progress`,
    `p`.`name` AS `project_name`,
    `p`.`project_code` AS `project_code`,
    `u`.`id` AS `reporter_id`,
    `u`.`name` AS `reporter_name`
FROM (((`biz_task_progress_log` `l`
    JOIN `biz_task` `t` ON ((`l`.`task_id` = `t`.`id`)))
    JOIN `biz_project` `p` ON ((`l`.`project_id` = `p`.`id`)))
    JOIN `sys_user` `u` ON ((`l`.`reporter_id` = `u`.`id`)))
WHERE `l`.`review_status` = 'pending';

-- ============================================================================
-- 五、删除工作量单位字典数据
-- ============================================================================
-- 8. 删除 workload_unit 字典数据
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'workload_unit';

-- 9. 删除 workload_unit 字典类型
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'workload_unit';
