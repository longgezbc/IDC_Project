-- ============================================================================
-- 应用用户权限初始化脚本（每次部署都执行）
-- 解决：MySQL 数据卷复用时 01-schema.sql 不重新执行，导致 hbsz_app 缺少权限
-- 说明：此脚本由 mysql-grants 容器在 MySQL 启动后执行，确保权限始终正确
-- 密码同步：hbsz_app 的密码由 mysql-grants 容器通过 ALTER USER 同步为
--          .env.docker 中的 DB_PASSWORD，此处的 hbsz_app_2026 仅作占位
-- ============================================================================

-- 确保用户存在（密码由 mysql-grants 容器在执行本脚本前通过 ALTER USER 同步）
CREATE USER IF NOT EXISTS 'hbsz_app'@'%' IDENTIFIED BY 'hbsz_app_2026';

-- 应用用户需要完整的 DDL/DML 权限以支持 Flyway 迁移
GRANT ALL PRIVILEGES ON `hbsz_idc_system`.* TO 'hbsz_app'@'%';

-- Flyway 需要读取 performance_schema 中的系统变量（foreign_key_checks、user_variables_by_thread）
GRANT SELECT ON performance_schema.* TO 'hbsz_app'@'%';

-- MySQL 8.0+ 迁移脚本中使用 SET @变量 + PREPARE 动态 SQL 需要 SYSTEM_USER 权限
GRANT SYSTEM_USER ON *.* TO 'hbsz_app'@'%';

FLUSH PRIVILEGES;
