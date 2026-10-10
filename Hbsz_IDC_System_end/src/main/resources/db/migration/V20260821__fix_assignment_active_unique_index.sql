-- =============================================================================
-- 修复 biz_project_assignment 唯一索引设计缺陷 迁移脚本
-- =============================================================================
-- 背景与根因（生产事故 2026-08-21 01:20）：
--   现索引 uk_project_engineer_active_scope(project_id, engineer_id, is_active,
--   permission_scope) 将 is_active 纳入唯一键，限制"同一组合只能有一条
--   is_active=0 的撤销历史"。而软删模式的正常语义是：同一 project+engineer
--   可反复 授权→撤销→再授权→再撤销，每次撤销均产生一条 is_active=0 记录。
--   因此工程师一旦经历过一次"撤销后再授权"，第二次撤销/项目转移（批量软删
--   置 is_active=0）必然与历史撤销记录撞唯一索引：
--     Duplicate entry '24-16-0-read_write' for key
--     'biz_project_assignment.uk_project_engineer_active_scope'
--   （V20260704.1 建表注释早已指出该约束不可加，01-schema.sql 引入时违背）
--
-- 修复方案（对齐 V20260819.1 的函数索引手法）：
--   1. 删除坏索引 uk_project_engineer_active_scope；
--   2. 清理可能存在的重复活跃记录（无索引环境的并发脏数据兜底，
--      保留每组最新一条，其余软删——天然幂等，二次执行无行可清）；
--   3. 创建函数唯一索引 uk_project_engineer_active：is_active=1 时取
--      CONCAT(project_id,'_',engineer_id,'_',permission_scope)，其余为 NULL——
--      MySQL 唯一索引对 NULL 不生效，历史撤销记录不受约束，仅保证
--      "同一 (project, engineer, scope) 至多一条活跃授权"（并发授权 DB 兜底）。
--
-- 风险评估：
--   - DROP INDEX / CREATE INDEX 均为 INPLACE 二级索引操作，不重建表、
--     不触碰外键（规避 1215），表量级小（<100 行），秒级完成；
--   - 清理 DML 仅触及重复活跃行（正常数据为 0 行），幂等可重跑；
--   - 回滚：坏索引属设计错误，不应恢复；如需紧急回退仅重建坏索引即可
--     （业务会再次触发同故障，不建议）。
--
-- 特性：
--   - 全程幂等（information_schema 存在性检查 + PREPARE/EXECUTE 模式）
--   - 不使用 USE 语句（数据源 URL 已指定数据库）
--
-- 作者：hbsz  日期：2026-08-21
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 删除坏索引 uk_project_engineer_active_scope（幂等）
--    注意：本脚本统一处理三种存量库形态——
--    a) 带坏索引（01-schema.sql / 手工建）→ 删除；
--    b) 无索引（V20260704.1 Flyway 建表）→ 跳过；
--    c) 已执行过本脚本 → 跳过。
-- -----------------------------------------------------------------------------
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project_assignment'
      AND INDEX_NAME = 'uk_project_engineer_active_scope');

SET @sql = IF(@idx_exists > 0,
    'DROP INDEX `uk_project_engineer_active_scope` ON `biz_project_assignment`',
    'SELECT ''skip: bad index uk_project_engineer_active_scope not exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. 清理重复活跃记录（幂等兜底）
--    同一 (project_id, engineer_id, permission_scope) 存在多条 is_active=1
--    时，仅保留 id 最大（最新）一条，其余软删。无索引环境的并发窗口可能
--    已产生此类脏数据，不清则第 3 步建索引会因唯一冲突失败。
--    正常库此语句影响 0 行。
-- -----------------------------------------------------------------------------
UPDATE biz_project_assignment a
JOIN (
    SELECT `project_id`, `engineer_id`, `permission_scope`, MAX(`id`) AS keep_id
    FROM biz_project_assignment
    WHERE `is_active` = 1
    GROUP BY `project_id`, `engineer_id`, `permission_scope`
    HAVING COUNT(*) > 1
) d ON a.`project_id` = d.`project_id`
   AND a.`engineer_id` = d.`engineer_id`
   AND a.`permission_scope` = d.`permission_scope`
   AND a.`is_active` = 1
   AND a.`id` <> d.keep_id
SET a.`is_active` = 0,
    a.`revoke_reason` = '系统清理：重复活跃授权，保留最新一条（迁移 V20260821）',
    a.`revoked_at` = NOW();

-- -----------------------------------------------------------------------------
-- 3. 幂等创建函数唯一索引（仅活跃记录唯一；MySQL 8.0.13+，生产 8.0.40）
--    注意函数索引表达式需双层括号：外层为索引列清单，内层标记函数表达式
-- -----------------------------------------------------------------------------
SET @idx2_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project_assignment'
      AND INDEX_NAME = 'uk_project_engineer_active');

SET @sql = IF(@idx2_exists = 0,
    CONCAT('CREATE UNIQUE INDEX `uk_project_engineer_active` ON `biz_project_assignment` ',
           '((CASE WHEN `is_active` = 1 THEN CONCAT(`project_id`, ''_'', `engineer_id`, ''_'', `permission_scope`) ELSE NULL END))'),
    'SELECT ''skip: uk_project_engineer_active already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
