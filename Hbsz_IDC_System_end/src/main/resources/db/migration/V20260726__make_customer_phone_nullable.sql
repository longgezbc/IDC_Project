-- ============================================================================
-- 迁移脚本：将 biz_customer.contact_phone 改为可空
-- ============================================================================
-- 背景：业务要求联系电话非必填（客户创建/编辑时可不填手机号）。
--       原表结构 contact_phone varchar(20) NOT NULL，需放宽为可空。
--       同步调整：后端 CustomerSaveDTO 已移除 @NotBlank，
--       CustomerServiceImpl 已对 null 做防御性处理（不调用 trim()）。
--
-- 影响范围：
--   - biz_customer: ALTER COLUMN contact_phone DROP NOT NULL
--
-- 特性：
--   - 幂等：通过 information_schema.COLUMNS.IS_NULLABLE 判断当前是否可为空，
--     已为 'YES' 时跳过，可重复执行
--
-- 注：Flyway 执行时已通过 datasource URL 指定数据库，无需 USE 语句
-- ============================================================================
-- 作者：hbsz  日期：2026-07-26
-- ============================================================================

-- 检查 contact_phone 列当前是否为 NOT NULL，若是则改为可空
SET @is_nullable = (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_customer' AND COLUMN_NAME = 'contact_phone');
SET @sql = IF(@is_nullable = 'NO',
    'ALTER TABLE `biz_customer` MODIFY COLUMN `contact_phone` varchar(20) DEFAULT NULL COMMENT ''联系电话''',
    'SELECT ''biz_customer.contact_phone already nullable'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
