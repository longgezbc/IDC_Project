-- =============================================================================
-- 工作量统计模块 迁移脚本
-- 功能：
--   1. 创建 biz_workload_item 工作量清单表（模板行：作业类型/作业范围/额定/单位/绩效分/总量）
--   2. 创建 biz_workload_record 每日填报明细表（模板单元格：日期/人员/数量）
--   3. 插入 workload:* 权限并绑定 admin / project_manager / engineer 角色
-- 背景：依据《工作量统计模板.xlsx》设计——项目定义作业清单（15 类 106 项标准目录），
--       施工人员按日填报各作业完成量，系统实时聚合已完成/剩余/今日量，
--       并按 绩效分 = Σ(数量÷额定工作量×绩效权重) 折算个人标准工日绩效。
--       注意：与 V20260715 移除的 project/task 级简单工作量字段无关，本模块为独立设计。
-- 作者：hbsz
-- 日期：2026-08-17
-- 说明：
--   1. 建表使用 CREATE TABLE IF NOT EXISTS（幂等，可重复执行）。
--   2. 权限插入按 permission_code 存在性判断（幂等）。
--   3. 不使用 USE 语句（数据源 URL 已指定数据库）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 工作量清单表 biz_workload_item
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_workload_item` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`        BIGINT          NOT NULL                COMMENT '项目ID（FK→biz_project.id）',
    `category`          VARCHAR(100)    NOT NULL                COMMENT '作业类型（如：24/48/96芯光缆）',
    `work_scope`        VARCHAR(200)    NOT NULL                COMMENT '作业范围（如：光缆上架（30-60米））',
    `rated_workload`    DECIMAL(12,2)   NOT NULL DEFAULT 1      COMMENT '额定工作量（每人每日配额，绩效折算基数）',
    `unit`              VARCHAR(20)     NOT NULL                COMMENT '计量单位（米/条/箱/芯/台/张/柜/组/个）',
    `performance_score` DECIMAL(8,2)    NOT NULL DEFAULT 1      COMMENT '绩效分权重',
    `total_quantity`    DECIMAL(12,2)   NOT NULL DEFAULT 0      COMMENT '工作总量（计划工程量）',
    `remark`            VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
    `sort_order`        INT             NOT NULL DEFAULT 0      COMMENT '排序号（模板行序）',
    `created_at`        DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_wli_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作量清单（作业类型×作业范围目录，按项目维护）';

-- -----------------------------------------------------------------------------
-- 2. 每日填报明细表 biz_workload_record
--    人员建模说明：模板中的施工人员为外包工人（非系统用户），故采用 worker_name
--    自由文本建模；唯一约束保证 同一清单项+同一日期+同一人员 仅一条记录。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_workload_record` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID（冗余，加速按项目+日期聚合）',
    `item_id`      BIGINT        NOT NULL                COMMENT '工作量清单项ID（FK→biz_workload_item.id）',
    `record_date`  DATE          NOT NULL                COMMENT '填报日期（工作量归属日）',
    `worker_name`  VARCHAR(50)   NOT NULL                COMMENT '施工人员姓名（自由文本，兼容外包人员）',
    `quantity`     DECIMAL(12,2) NOT NULL                COMMENT '当日完成数量',
    `remark`       VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    `created_by`   BIGINT        DEFAULT NULL            COMMENT '填报人用户ID（FK→sys_user.id）',
    `created_at`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wlr_item_date_worker` (`item_id`, `record_date`, `worker_name`),
    KEY `idx_wlr_project_date` (`project_id`, `record_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作量每日填报明细（人员×清单项×日期）';

-- -----------------------------------------------------------------------------
-- 3. 权限数据（工作量统计：菜单 + 填报/维护按钮）
--    父级挂在 statistics 权限下（数据分析菜单组），不存在则挂顶级（parent_id=0）。
-- -----------------------------------------------------------------------------
SET @stats_parent := (SELECT `id` FROM (SELECT `id` FROM `sys_permission` WHERE `permission_code` = 'statistics' ORDER BY `id` LIMIT 1) AS t);

-- 3.1 菜单权限：工作量统计（查看）
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT IFNULL(@stats_parent, 0), '工作量统计', 'workload:query', 1, 'workload', 'workload/WorkloadStatistics', 'Histogram', 3, 1, 1, '工作量清单维护、每日填报与绩效统计'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'workload:query') AS t1);

SET @wl_parent := (SELECT `id` FROM (SELECT `id` FROM `sys_permission` WHERE `permission_code` = 'workload:query' LIMIT 1) AS t);

-- 3.2 按钮权限：填报
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT @wl_parent, '工作量填报', 'workload:report', 2, '', NULL, '', 1, 0, 1, '按日填报施工人员工作量'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'workload:report') AS t2);

-- 3.3 按钮权限：清单维护
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT @wl_parent, '工作量清单维护', 'workload:edit', 2, '', NULL, '', 2, 0, 1, '维护项目工作量清单（新增/编辑/删除/导入模板）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'workload:edit') AS t3);

-- 3.4 按钮权限：导出
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT @wl_parent, '工作量导出', 'workload:export', 2, '', NULL, '', 3, 0, 1, '导出工作量统计 Excel'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'workload:export') AS t4);

-- -----------------------------------------------------------------------------
-- 4. 角色绑定
--    admin / project_manager：全部 workload 权限；engineer：query + report（可填报）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` IN ('workload:query', 'workload:report', 'workload:edit', 'workload:export')
WHERE r.`role_code` IN ('admin', 'project_manager')
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`) AS t5
  );

INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` IN ('workload:query', 'workload:report')
WHERE r.`role_code` = 'engineer'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`) AS t6
  );
