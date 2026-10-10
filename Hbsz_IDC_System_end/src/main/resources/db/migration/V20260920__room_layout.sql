-- =============================================================================
-- 机房布局编辑器 迁移脚本
-- 功能：
--   1. 创建 biz_room 机房网格定义表（项目 → 多机房，网格列/行/单元格尺寸）
--   2. 创建 biz_room_object 布局元素表（机柜/桥架/墙体/立柱，格坐标矩形 + 扩展 JSON）
--   3. 创建 biz_cable_run 线缆表（两端机柜端口，长度由路由算法计算落库）
--   4. 插入 room:* 权限并绑定 admin / project_manager / engineer 角色
-- 背景：机房布局编辑器（AntV X6）——自由拖拽编辑机柜与桥架，
--       依据布局沿桥架路由计算线缆长度（L1 曼哈顿估算 / L2 桥架 Dijkstra 精确），
--       计算结果可对接 106 项工作量清单的线缆类条目填报。
-- 作者：hbsz
-- 日期：2026-09-20
-- 说明：
--   1. 建表使用 CREATE TABLE IF NOT EXISTS（幂等，可重复执行）。
--   2. 权限插入按 permission_code 存在性判断（幂等）。
--   3. 不使用 USE 语句（数据源 URL 已指定数据库）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 机房网格定义表 biz_room
--    坐标体系：网格 cols × rows，每格 cell_size_mm 毫米（典型机柜位 600mm）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_room` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`   BIGINT       NOT NULL                COMMENT '项目ID（FK→biz_project.id）',
    `name`         VARCHAR(100) NOT NULL                COMMENT '机房名称',
    `grid_cols`    INT          NOT NULL DEFAULT 40     COMMENT '网格列数',
    `grid_rows`    INT          NOT NULL DEFAULT 30     COMMENT '网格行数',
    `cell_size_mm` INT          NOT NULL DEFAULT 600    COMMENT '单元格实际尺寸（毫米）',
    `description`  VARCHAR(255) DEFAULT NULL            COMMENT '描述',
    `created_by`   BIGINT       DEFAULT NULL            COMMENT '创建人用户ID（FK→sys_user.id）',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_room_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '机房布局网格定义（项目 → 多机房）';

-- -----------------------------------------------------------------------------
-- 2. 布局元素表 biz_room_object
--    统一矩形建模：左上角格坐标 (x,y) + 占格尺寸 (w,h)。
--    height_mm 语义随类型：rack=设备顶面高度；tray=离地敷设高度；wall/pillar 可空。
--    payload 为 JSON 扩展字段（端口数/U位数/颜色等），避免频繁加列。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_room_object` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_id`    BIGINT       NOT NULL                COMMENT '机房ID（FK→biz_room.id）',
    `kind`       VARCHAR(20)  NOT NULL                COMMENT '元素类型：rack机柜/tray桥架/wall墙体/pillar立柱',
    `label`      VARCHAR(100) DEFAULT NULL            COMMENT '元素标签（如 A-01 / 强电桥架-1）',
    `x`          INT          NOT NULL                COMMENT '左上角格坐标X',
    `y`          INT          NOT NULL                COMMENT '左上角格坐标Y',
    `w`          INT          NOT NULL DEFAULT 1      COMMENT '占格宽（格数）',
    `h`          INT          NOT NULL DEFAULT 1      COMMENT '占格高（格数）',
    `height_mm`  INT          DEFAULT NULL            COMMENT '高度毫米（rack=设备顶面高度；tray=离地敷设高度）',
    `payload`    TEXT         DEFAULT NULL            COMMENT '扩展属性JSON（端口数/U位数/颜色等）',
    `created_at` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ro_room` (`room_id`),
    KEY `idx_ro_room_kind` (`room_id`, `kind`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '机房布局元素（机柜/桥架/墙体/立柱，格坐标矩形）';

-- -----------------------------------------------------------------------------
-- 3. 线缆表 biz_cable_run
--    两端挂机柜端口；route_mode 决定算法（L1 估算 / L2 沿桥架 Dijkstra）；
--    length_mm 为含垂直爬升、端接预留与裕量系数的最终计算结果；
--    calc_detail JSON 记录分段明细与路径点（前端可复现绘制）。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_cable_run` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `room_id`        BIGINT       NOT NULL                COMMENT '机房ID（FK→biz_room.id）',
    `label`          VARCHAR(100) DEFAULT NULL            COMMENT '线缆标签',
    `cable_type`     VARCHAR(50)  DEFAULT NULL            COMMENT '线缆型号（如 24芯光缆/CAT6A）',
    `from_object_id` BIGINT       NOT NULL                COMMENT '起始元素ID（FK→biz_room_object.id，机柜）',
    `from_port`      VARCHAR(50)  DEFAULT NULL            COMMENT '起始端口',
    `to_object_id`   BIGINT       NOT NULL                COMMENT '目标元素ID（FK→biz_room_object.id，机柜）',
    `to_port`        VARCHAR(50)  DEFAULT NULL            COMMENT '目标端口',
    `route_mode`     VARCHAR(10)  NOT NULL DEFAULT 'L2'   COMMENT '路由模式：L1曼哈顿估算/L2沿桥架精确',
    `slack_pct`      DECIMAL(5,2) NOT NULL DEFAULT 15     COMMENT '裕量系数（百分比）',
    `length_mm`      BIGINT       NOT NULL DEFAULT 0      COMMENT '计算线缆长度（毫米，含裕量与端接预留）',
    `calc_detail`    TEXT         DEFAULT NULL            COMMENT '计算明细JSON（分段长度/路径点/回退标记）',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cr_room` (`room_id`),
    KEY `idx_cr_from` (`from_object_id`),
    KEY `idx_cr_to` (`to_object_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '机房线缆（两端机柜端口，长度由路由算法计算）';

-- -----------------------------------------------------------------------------
-- 4. 权限数据（机房布局：菜单 + 编辑/计算按钮）
--    父级挂在 project 项目管理菜单组下，不存在则挂顶级（parent_id=0）。
-- -----------------------------------------------------------------------------
SET @project_parent := (SELECT `id` FROM (SELECT `id` FROM `sys_permission` WHERE `permission_code` = 'project' ORDER BY `id` LIMIT 1) AS t);

-- 4.1 菜单权限：机房布局（查看）
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT IFNULL(@project_parent, 0), '机房布局', 'room:query', 1, '/room', 'room/RoomLayoutList', 'Grid', 70, 1, 1, '机房网格布局编辑与线缆长度计算'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'room:query') AS t1);

SET @room_parent := (SELECT `id` FROM (SELECT `id` FROM `sys_permission` WHERE `permission_code` = 'room:query' LIMIT 1) AS t);

-- 4.2 按钮权限：布局编辑
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT @room_parent, '机房布局编辑', 'room:edit', 2, '', NULL, '', 1, 0, 1, '编辑机房网格、机柜、桥架与线缆'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'room:edit') AS t2);

-- 4.3 按钮权限：线缆长度计算
INSERT INTO `sys_permission`(`parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`)
SELECT @room_parent, '线缆长度计算', 'room:calc', 2, '', NULL, '', 2, 0, 1, '触发线缆长度精确计算（沿桥架路由）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM `sys_permission` WHERE `permission_code` = 'room:calc') AS t3);

-- -----------------------------------------------------------------------------
-- 5. 角色绑定
--    admin / project_manager：全部 room 权限；engineer：query（只读查看）
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` IN ('room:query', 'room:edit', 'room:calc')
WHERE r.`role_code` IN ('admin', 'project_manager')
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`) AS t4
  );

INSERT INTO `sys_role_permission`(`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p ON p.`permission_code` = 'room:query'
WHERE r.`role_code` = 'engineer'
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT 1 FROM `sys_role_permission` rp
                   WHERE rp.`role_id` = r.`id` AND rp.`permission_id` = p.`id`) AS t5
  );
