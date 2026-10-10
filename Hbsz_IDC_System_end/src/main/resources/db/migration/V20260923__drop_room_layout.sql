-- =============================================================================
-- 机房布局功能下线迁移脚本
-- 功能：
--   1. 回收 room:query / room:edit / room:calc 权限及其全部角色绑定
--   2. 删除 biz_cable_run / biz_room_object / biz_room 三张业务表
-- 背景：机房布局功能（V20260920 上线）整体下线，前后端代码已删除；
--       本脚本负责数据库侧的终态清理，保证功能不可复活。
-- 作者：hbsz
-- 日期：2026-09-23
-- 说明：
--   1. V20260920__room_layout.sql 为已应用迁移，按 Flyway 规范保留不动，
--      本脚本在其之后执行，净效果 = 功能从未存在（全新库亦然）。
--   2. 删除前必须先备份三张表（生产库可能存在用户绘制的布局数据）：
--      mysqldump -h<host> -u<user> -p <db> biz_room biz_room_object biz_cable_run \
--        > backup_room_layout_$(date +%Y%m%d).sql
--   3. 全部语句幂等（IF EXISTS / 按存在性删除），可重复执行。
--   4. 不使用 USE 语句（数据源 URL 已指定数据库）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 回收角色绑定（依赖方先行：先删 sys_role_permission 再删 sys_permission）
-- -----------------------------------------------------------------------------
DELETE rp FROM `sys_role_permission` rp
JOIN `sys_permission` p ON p.`id` = rp.`permission_id`
WHERE p.`permission_code` IN ('room:query', 'room:edit', 'room:calc');

-- -----------------------------------------------------------------------------
-- 2. 回收权限定义（菜单 + 按钮）
-- -----------------------------------------------------------------------------
DELETE FROM `sys_permission`
WHERE `permission_code` IN ('room:query', 'room:edit', 'room:calc');

-- -----------------------------------------------------------------------------
-- 3. 删除业务表（依赖方先行：biz_cable_run → biz_room_object → biz_room）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `biz_cable_run`;
DROP TABLE IF EXISTS `biz_room_object`;
DROP TABLE IF EXISTS `biz_room`;
