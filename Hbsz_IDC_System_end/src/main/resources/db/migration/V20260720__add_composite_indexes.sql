-- ============================================================================
-- 数据库索引补全迁移脚本
-- ----------------------------------------------------------------------------
-- 背景：
--   系统评估发现 schema 索引设计整体完善（已覆盖单列索引与主要复合索引），
--   仅有 3 个高频查询场景的复合索引缺失，本脚本补齐这些缺口。
--
-- 新增索引说明：
--   1. idx_project_review_time on biz_task_progress_log(project_id, review_status, report_time)
--      —— 支撑 PM 在项目维度下查看待审核进度日志的场景（按时间倒序）。
--      原有 idx_project_id 单列索引只能定位项目，无法在 review_status + report_time
--      上做高效过滤；新复合索引可直接定位"某项目 pending 状态的进度日志"。
--
--   2. idx_project_status_category on biz_document(project_id, status, category)
--      —— 支撑文档管理页"按状态+分类筛选项目文档"的复合查询。
--      原有 idx_project_id、idx_status、idx_category 为单列索引，需依赖索引合并
--      （index merge）效率不稳定；新复合索引覆盖三字段，查询直接走索引扫描。
--
--   3. idx_user_created on sys_operation_log(user_id, created_at)
--      —— 支撑"我的操作记录"页面（按用户筛选 + 时间倒序）。
--      原有 idx_user_id 单列索引无法在 created_at 上排序，需回表后 filesort；
--      新复合索引可在索引层完成排序，避免 filesort 开销。
--
-- 特性：
--   - 幂等：使用 information_schema 检查索引是否存在，重复执行不报错
--   - 安全：仅 ADD INDEX，无 DROP/ALTER COLUMN 等破坏性操作
--
-- 评估依据：基于 EXPLAIN 分析常见查询场景得出，预计可减少 30-50% 的回表操作
-- 作者：hbsz  日期：2026-07-20
-- ============================================================================

-- ============================================================================
-- 1. biz_task_progress_log: PM 项目维度审核队列查询
-- ============================================================================
-- 场景：SELECT * FROM biz_task_progress_log
--       WHERE project_id = ? AND review_status = 'pending'
--       ORDER BY report_time DESC LIMIT 20;

-- 检查索引是否存在，不存在则添加（幂等）
SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'biz_task_progress_log'
  AND index_name = 'idx_project_review_time';

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE biz_task_progress_log ADD INDEX idx_project_review_time (project_id, review_status, report_time)',
    'SELECT ''idx_project_review_time already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================================
-- 2. biz_document: 文档管理页复合筛选
-- ============================================================================
-- 场景：SELECT * FROM biz_document
--       WHERE project_id = ? AND status = '待审核' AND category = '技术文档'
--       ORDER BY id DESC LIMIT 20;

SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'biz_document'
  AND index_name = 'idx_project_status_category';

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE biz_document ADD INDEX idx_project_status_category (project_id, status, category)',
    'SELECT ''idx_project_status_category already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================================
-- 3. sys_operation_log: "我的操作记录"页面
-- ============================================================================
-- 场景：SELECT * FROM sys_operation_log
--       WHERE user_id = ? ORDER BY created_at DESC LIMIT 20;

SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'sys_operation_log'
  AND index_name = 'idx_user_created';

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE sys_operation_log ADD INDEX idx_user_created (user_id, created_at)',
    'SELECT ''idx_user_created already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
