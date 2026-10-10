-- =============================================================================
-- 授权申请 pending 唯一防护 迁移脚本（v2：函数索引方案）
-- 功能：
--   1. 清理存量重复 pending 申请（同一 project + target_engineer 仅保留最早一条）
--   2. 创建函数唯一索引 uk_auth_request_pending（表达式索引，无新列）
-- 背景：
--   createAuthRequest 的 pending 查重为 check-then-act，并发提交可插入
--   两条 pending 申请；代码层已将 DuplicateKeyException 转为业务异常，
--   本索引为数据库层兜底，保证同一 (project, target_engineer) 至多一条 pending。
-- 方案演进（v1 → v2）：
--   v1 采用 STORED 生成列 + 普通唯一索引：ADD COLUMN ... STORED 强制 COPY
--   算法整表重建，重建时重新校验既有外键，在存量外键环境下稳定触发
--   1215 Cannot add foreign key constraint（即使会话级关闭 FOREIGN_KEY_CHECKS
--   仍无法通过），故整体放弃。
--   v2 改用函数唯一索引（MySQL 8.0.13+）：仅新增二级索引（INPLACE 算法），
--   不重建表、不触碰外键，从机制上规避 1215；且不产生物理列，实体映射零影响。
-- 作者：hbsz
-- 日期：2026-08-19
-- 说明：
--   1. 索引表达式：status='pending' 时值为 CONCAT(project_id,'_',target_engineer_id)，
--      非 pending 时为 NULL——MySQL 唯一索引对 NULL 不生效，天然实现"仅 pending 唯一"，
--      历史已审批/驳回记录不受约束。
--   2. 重复 pending 为并发缺陷产物，无 assignment 关联，直接删除安全；
--      保留最早一条（MIN(id)），后续审批复用。
--   3. 索引创建通过 information_schema 做存在性检查（幂等，可重复执行）。
--   4. 不使用 USE 语句（数据源 URL 已指定数据库）。
--   5. 【状态更新 2026-08-21】本脚本已在生产环境成功执行（Flyway success=1），
--      自此不可再修改脚本内容（Flyway checksum 校验）；新环境的初始 schema
--      已由 docker/mysql/01-schema.sql 同步携带 uk_auth_request_pending 索引。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 清理存量重复 pending（天然幂等：第二次执行无重复行可删）
-- -----------------------------------------------------------------------------
DELETE r FROM `biz_project_auth_request` r
JOIN (
    SELECT `project_id`, `target_engineer_id`, MIN(`id`) AS keep_id
    FROM `biz_project_auth_request`
    WHERE `status` = 'pending'
    GROUP BY `project_id`, `target_engineer_id`
    HAVING COUNT(*) > 1
) d ON r.`project_id` = d.`project_id`
   AND r.`target_engineer_id` = d.`target_engineer_id`
   AND r.`id` <> d.keep_id
   AND r.`status` = 'pending';

-- -----------------------------------------------------------------------------
-- 2. 幂等创建函数唯一索引（INPLACE 加二级索引，不重建表、不触碰外键）
--    注意函数索引表达式需要双层括号：外层为索引列清单，内层标记函数表达式
-- -----------------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project_auth_request'
      AND INDEX_NAME = 'uk_auth_request_pending');

SET @sql = IF(@idx_exists = 0,
    CONCAT('CREATE UNIQUE INDEX `uk_auth_request_pending` ON `biz_project_auth_request` ',
           '((CASE WHEN `status` = ''pending'' THEN CONCAT(`project_id`, ''_'', `target_engineer_id`) ELSE NULL END))'),
    'SELECT ''skip: uk_auth_request_pending already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
