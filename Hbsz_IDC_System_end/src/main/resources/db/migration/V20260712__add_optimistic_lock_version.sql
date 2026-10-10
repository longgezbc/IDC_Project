-- =============================================================================
-- 乐观锁版本字段迁移脚本（修复 #1：ProjectServiceImpl.update 并发覆盖风险）
-- 功能：为 biz_project 表添加 version 字段，配合 MyBatis-Plus OptimisticLockerInnerInterceptor
--       实现乐观锁，防止两人并发更新同一项目时后提交者覆盖前者的数据。
-- 作者：hbsz
-- 日期：2026-07-12
-- 说明：
--   1. 本项目未引入 Flyway，脚本需手动执行；DDL 做幂等处理，可重复执行。
--   2. 通过 information_schema 判断列是否存在后再执行 ALTER TABLE，避免重复执行报错。
--   3. 初始化 version = 1，后续每次 updateById 自动递增。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- biz_project 增加 version 列（乐观锁版本号）
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project'
      AND COLUMN_NAME = 'version');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_project` ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT ''乐观锁版本号（并发更新保护）''',
    'SELECT ''biz_project.version already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 初始化已有数据的 version 为 1（DEFAULT 1 已在 DDL 中完成，此处仅为显式确认）
UPDATE `biz_project` SET `version` = 1 WHERE `version` IS NULL OR `version` = 0;
