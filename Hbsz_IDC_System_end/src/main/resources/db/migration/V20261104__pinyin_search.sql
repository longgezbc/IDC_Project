-- =============================================================================
-- V20261104 全局搜索拼音匹配：三表加名称拼音冗余列
-- 功能：
--   biz_project / biz_document / biz_customer 各加两列：
--     name_pinyin_full     名称全拼（小写，如 tengxun wulanchabu jianan gw m5）
--     name_pinyin_initials 名称拼音首字母（小写，如 txwlcbjagwm5）
-- 背景：全局搜索按 name/project_code 字面 LIKE 匹配，输入拼音（如 JIANAN）
--       无法命中中文名称（建安）。应用层（MetaObjectHandler）随 name 自动生成
--       拼音冗余串，搜索时对拼音列 OR 匹配，实现拼音/首字母检索。
-- 回量：存量数据由应用启动时的 PinyinBackfillRunner 幂等回量
--       （汉字→拼音需 Java 侧 TinyPinyin 转换，SQL 无法完成）。
-- 作者：hbsz
-- 日期：2026-11-04
-- 幂等：information_schema 存在性检查 + PREPARE/EXECUTE，重复执行安全。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. biz_project 加列
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project'
      AND COLUMN_NAME = 'name_pinyin_full');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_project` ADD COLUMN `name_pinyin_full` VARCHAR(255) DEFAULT NULL COMMENT ''名称全拼（小写冗余，供全局搜索拼音匹配）'' AFTER `name`',
    'SELECT ''biz_project.name_pinyin_full already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_project'
      AND COLUMN_NAME = 'name_pinyin_initials');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_project` ADD COLUMN `name_pinyin_initials` VARCHAR(255) DEFAULT NULL COMMENT ''名称拼音首字母（小写冗余，供全局搜索拼音匹配）'' AFTER `name_pinyin_full`',
    'SELECT ''biz_project.name_pinyin_initials already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. biz_document 加列
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_document'
      AND COLUMN_NAME = 'name_pinyin_full');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_document` ADD COLUMN `name_pinyin_full` VARCHAR(255) DEFAULT NULL COMMENT ''名称全拼（小写冗余，供全局搜索拼音匹配）'' AFTER `name`',
    'SELECT ''biz_document.name_pinyin_full already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_document'
      AND COLUMN_NAME = 'name_pinyin_initials');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_document` ADD COLUMN `name_pinyin_initials` VARCHAR(255) DEFAULT NULL COMMENT ''名称拼音首字母（小写冗余，供全局搜索拼音匹配）'' AFTER `name_pinyin_full`',
    'SELECT ''biz_document.name_pinyin_initials already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3. biz_customer 加列
-- -----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_customer'
      AND COLUMN_NAME = 'name_pinyin_full');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_customer` ADD COLUMN `name_pinyin_full` VARCHAR(255) DEFAULT NULL COMMENT ''名称全拼（小写冗余，供全局搜索拼音匹配）'' AFTER `name`',
    'SELECT ''biz_customer.name_pinyin_full already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'biz_customer'
      AND COLUMN_NAME = 'name_pinyin_initials');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `biz_customer` ADD COLUMN `name_pinyin_initials` VARCHAR(255) DEFAULT NULL COMMENT ''名称拼音首字母（小写冗余，供全局搜索拼音匹配）'' AFTER `name_pinyin_full`',
    'SELECT ''biz_customer.name_pinyin_initials already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
