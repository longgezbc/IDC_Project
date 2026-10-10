-- ============================================================
-- 数据修复脚本：回填 biz_project.manager_id 和 biz_task.assignee_id
-- ============================================================
-- 背景：历史数据中 manager_id / assignee_id 字段为 NULL，导致 PM 工作台统计、
--       风险看板、进度审核通知、「我的任务」列表、到期通知等功能无法匹配到具体用户。
-- 修复策略：通过 manager_name / assignee_name 与 sys_user.name 进行关联回填。
-- 执行前提：sys_user 表中存在对应姓名的用户记录。
-- 幂等性：仅更新 manager_id IS NULL 或 assignee_id IS NULL 的记录，可重复执行。
-- ============================================================

-- 1. 回填 biz_project.manager_id（通过 manager_name 关联 sys_user.name）
UPDATE biz_project p
INNER JOIN sys_user u ON p.manager_name = u.name
SET p.manager_id = u.id
WHERE p.manager_id IS NULL
  AND p.manager_name IS NOT NULL
  AND p.manager_name != '';

-- 2. 回填 biz_task.assignee_id（通过 assignee_name 关联 sys_user.name）
UPDATE biz_task t
INNER JOIN sys_user u ON t.assignee_name = u.name
SET t.assignee_id = u.id
WHERE t.assignee_id IS NULL
  AND t.assignee_name IS NOT NULL
  AND t.assignee_name != '';

-- 3. 统计回填结果（用于人工核对）
SELECT
    'project_manager_backfill' AS target,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN manager_id IS NOT NULL THEN 1 ELSE 0 END) AS filled_count,
    SUM(CASE WHEN manager_id IS NULL AND manager_name IS NOT NULL AND manager_name != '' THEN 1 ELSE 0 END) AS still_null_count
FROM biz_project
UNION ALL
SELECT
    'task_assignee_backfill' AS target,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN assignee_id IS NOT NULL THEN 1 ELSE 0 END) AS filled_count,
    SUM(CASE WHEN assignee_id IS NULL AND assignee_name IS NOT NULL AND assignee_name != '' THEN 1 ELSE 0 END) AS still_null_count
FROM biz_task;
