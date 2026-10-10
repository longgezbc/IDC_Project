-- ============================================================
-- 迁移脚本：biz_project 表新增 region（项目地区）字段
-- ============================================================
-- 背景：项目表单需支持省/市/区三级地区选择器（el-cascader），选择结果
--       以 "/" 分隔的字符串存入 region 字段（如 "广东省/深圳市/南山区"）。
--       前端 el-cascader 选中值为数组，提交时 join('/')，回显时 split('/')。
--
-- 字段说明：
--   region VARCHAR(100)：足够容纳 "XX省/XX市/XX区"（最长约 30 字符）
--   位于 address 字段之后，逻辑上同属"地理位置"分组
--
-- 特性：
--   - 幂等：使用 information_schema 检查列是否存在，重复执行不报错
--   - 安全：仅 ADD COLUMN，无 DROP/ALTER 等破坏性操作
--
-- 同步：Docker init 01-schema.sql 的 biz_project 表也已加入 region 字段，
--       全新部署时由 01-schema.sql 创建，已有数据库由本脚本补充
--
-- 作者：hbsz  日期：2026-07-20
-- ============================================================

-- 检查 region 列是否存在，不存在则添加（幂等）
SELECT COUNT(1) INTO @col_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'biz_project'
  AND column_name = 'region';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE biz_project ADD COLUMN region VARCHAR(100) DEFAULT NULL COMMENT ''项目地区（省/市/区，/ 分隔）'' AFTER address',
    'SELECT ''region column already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
