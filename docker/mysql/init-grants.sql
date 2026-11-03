-- ============================================================================
-- 应用用户权限初始化脚本（每次部署都执行）
-- 解决：MySQL 数据卷复用时 01-schema.sql 不重新执行，导致账号缺少权限
-- 说明：此脚本由 mysql-grants 容器在 MySQL 启动后执行，确保权限始终正确
-- 密码同步：hbsz_app / hbsz_migrate 的密码由 mysql-grants 容器通过 ALTER USER
--          同步为 .env.docker 中的 DB_PASSWORD / FLYWAY_PASSWORD，此处占位密码仅作建用户用
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 应用运行时账号 hbsz_app：最小权限，仅 DML
-- 安全加固（P1-2）：原持有 ALL PRIVILEGES + SYSTEM_USER，违反最小权限原则。
-- 一旦应用被 SQL 注入或 RCE，攻击者可删库删表、绕过权限校验。
-- 现收窄为 DML；DDL 与动态 SQL（PREPARE）所需权限改由独立迁移账号 hbsz_migrate 持有。
-- ----------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'hbsz_app'@'%' IDENTIFIED BY 'hbsz_app_2026';
-- 先回收旧权限再授予最小权限：GRANT 只做叠加，旧数据卷上已有的
-- ALL PRIVILEGES / SYSTEM_USER 不会被覆盖，必须显式 REVOKE（SYSTEM_USER
-- 属动态权限，REVOKE ALL PRIVILEGES 不会回收，需单独执行）
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hbsz_app'@'%';
REVOKE SYSTEM_USER ON *.* FROM 'hbsz_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `hbsz_idc_system`.* TO 'hbsz_app'@'%';

-- ----------------------------------------------------------------------------
-- 2. Flyway 迁移账号 hbsz_migrate：DDL + 动态 SQL 权限
-- 迁移脚本使用 SET @变量 + PREPARE/EXECUTE 模式做幂等 DDL，MySQL 8.0+ 需 SYSTEM_USER 权限。
-- 该账号仅由 Flyway 在应用启动迁移阶段使用，运行时代码通过 hbsz_app（DML）连接，互不干扰。
-- 长期建议：将 'hbsz_migrate'@'%' 与 'hbsz_app'@'%' 收窄为容器来源网段（如 @'10.%'）。
-- ----------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'hbsz_migrate'@'%' IDENTIFIED BY 'hbsz_migrate_2026';
GRANT ALL PRIVILEGES ON `hbsz_idc_system`.* TO 'hbsz_migrate'@'%';
-- Flyway 需要读取 performance_schema 系统变量（foreign_key_checks、user_variables_by_thread）
GRANT SELECT ON performance_schema.* TO 'hbsz_migrate'@'%';
-- MySQL 8.0+ 迁移脚本中 SET @变量 + PREPARE 动态 SQL 需要 SYSTEM_USER 权限
GRANT SYSTEM_USER ON *.* TO 'hbsz_migrate'@'%';

FLUSH PRIVILEGES;
