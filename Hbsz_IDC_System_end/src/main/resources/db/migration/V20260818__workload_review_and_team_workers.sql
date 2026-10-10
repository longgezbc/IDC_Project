-- =============================================================================
-- 工作量统计：填报成员项目组化 + 审核工作流 迁移脚本
-- 功能：
--   1. biz_workload_record 新增 worker_user_id（施工人员关联项目组成员）
--   2. biz_workload_record 新增审核字段（review_status/reviewer/review_time/review_remark）
--   3. 存量填报数据一律置为 approved（保持历史统计口径连续，不因引入审核而清零）
--   4. 新增 workload:review 权限并绑定 admin / project_manager 角色
-- 背景：原设计 worker_name 为自由文本（模板施工人员为外包工人），
--       业务要求填报人员必须是项目组授权成员，且填报须经项目经理审核
--       （参照 biz_task_progress_log 的 pending/approved/rejected 模式），
--       统计口径调整为仅 approved 记录计入已完成量与绩效。
-- 作者：hbsz
-- 日期：2026-08-18
-- 说明：
--   1. DDL 通过 information_schema 做存在性检查（幂等，可重复执行）。
--   2. 不使用 USE 语句（数据源 URL 已指定数据库）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. worker_user_id 列（施工人员用户ID，FK→sys_user.id，项目组成员）
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_workload_record'
      AND COLUMN_NAME = 'worker_user_id');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_workload_record` ADD COLUMN `worker_user_id` BIGINT DEFAULT NULL COMMENT ''施工人员用户ID（FK→sys_user.id，须为项目组成员）'' AFTER `worker_name`',
    'SELECT ''skip: biz_workload_record.worker_user_id already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. 审核状态列 review_status（pending/approved/rejected，默认待审核）
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_workload_record'
      AND COLUMN_NAME = 'review_status');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_workload_record` ADD COLUMN `review_status` VARCHAR(20) NOT NULL DEFAULT ''pending'' COMMENT ''审核状态：pending待审核/approved已通过/rejected已驳回'' AFTER `remark`',
    'SELECT ''skip: biz_workload_record.review_status already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3. 审核人/审核时间/审核意见列
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_workload_record'
      AND COLUMN_NAME = 'reviewer_id');

SET @sql = IF(@col_exists = 0,
    CONCAT('ALTER TABLE `biz_workload_record` ',
           'ADD COLUMN `reviewer_id` BIGINT DEFAULT NULL COMMENT ''审核人用户ID（FK→sys_user.id）'' AFTER `review_status`, ',
           'ADD COLUMN `reviewer_name` VARCHAR(50) DEFAULT NULL COMMENT ''审核人姓名（冗余）'' AFTER `reviewer_id`, ',
           'ADD COLUMN `review_time` DATETIME DEFAULT NULL COMMENT ''审核时间'' AFTER `reviewer_name`, ',
           'ADD COLUMN `review_remark` VARCHAR(255) DEFAULT NULL COMMENT ''审核意见（驳回原因）'' AFTER `review_time`'),
    'SELECT ''skip: biz_workload_record reviewer columns already exist'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 4. 项目+审核状态复合索引（待审核列表按项目筛选）
-- -----------------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_workload_record'
      AND INDEX_NAME = 'idx_wlr_project_status');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `biz_workload_record` ADD INDEX `idx_wlr_project_status` (`project_id`, `review_status`)',
    'SELECT ''skip: biz_workload_record idx_wlr_project_status already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 5. 存量数据修复：历史填报（自由文本人员）一律视为已审核通过
--    保证统计口径连续——历史完成量/绩效不因引入审核机制而消失。
--    worker_user_id 保持 NULL（无法可靠回填），矩阵按 worker_name 兼容展示。
-- -----------------------------------------------------------------------------
UPDATE `biz_workload_record` SET `review_status` = 'approved' WHERE `review_status` = 'pending';

-- -----------------------------------------------------------------------------
-- 6. workload:review 按钮权限（审核填报），挂在工作量统计菜单下
-- -----------------------------------------------------------------------------
SET @wl_parent := (SELECT `id` FROM (SELECT `id` FROM `sys_permission` WHERE `permission_code` = 'workload:query' LIMIT 1) AS t);

INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT @wl_parent, '工作量审核', 'workload:review', 2, '', NULL, '', 4, 0, 1, '审核工作量填报（通过/驳回），仅项目经理与管理员'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'workload:review') AS t1);

-- -----------------------------------------------------------------------------
-- 7. 角色绑定：admin / project_manager 获得审核权限
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` = 'workload:review'
WHERE r.`role_code` IN ('admin', 'project_manager')
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`) AS t2
  );
