-- ============================================================================
-- 迁移脚本：将 biz_project_transfer_log.operated_by 改为可空
-- ============================================================================
-- 背景：授权过期自动回收定时任务（AssignmentExpireTask → AssignmentRevokeService）
--       写审计日志时 operated_by 为 NULL（系统自动操作无人类操作人），
--       而原列定义 NOT NULL 且无默认值，MySQL 严格模式下 INSERT 报 1364
--       （Field 'operated_by' doesn't have a default value），
--       导致软删与审计在同一事务内整体回滚，过期授权永不回收。
--
-- 方案：可空 + operator_name='系统定时任务'（而非哨兵用户ID）：
--   - 哨兵 ID 会让系统操作被误读为该用户的人工操作，损害审计可信度；
--   - NULL + 名称冗余列是审计域对"系统操作"的诚实表达；
--   - 外键 fk_ptl_operator 对 NULL 值不生效，无需调整约束；
--   - MODIFY COLUMN 仅放宽约束，元数据级操作（INPLACE），无数据重建。
--
-- 影响范围：
--   - biz_project_transfer_log: MODIFY COLUMN operated_by NULL
--   - 同步调整：docker/mysql/01-schema.sql（全新环境初始化）已同步为 NULL；
--     AssignmentRevokeService 已收敛复用 ProjectTransferLogHelper 写审计。
--
-- 特性：
--   - 幂等：通过 information_schema.COLUMNS.IS_NULLABLE 判断当前是否可为空，
--     已为 'YES' 时跳过，可重复执行
--
-- 注：Flyway 执行时已通过 datasource URL 指定数据库，无需 USE 语句
-- ============================================================================
-- 作者：hbsz  日期：2026-08-20
-- ============================================================================

-- 检查 operated_by 列当前是否为 NOT NULL，若是则改为可空
SET @is_nullable = (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project_transfer_log' AND COLUMN_NAME = 'operated_by');
SET @sql = IF(@is_nullable = 'NO',
    'ALTER TABLE `biz_project_transfer_log` MODIFY COLUMN `operated_by` bigint NULL COMMENT ''操作人ID（系统自动操作时为NULL）''',
    'SELECT ''biz_project_transfer_log.operated_by already nullable'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
