-- ============================================================================
-- 数据中心综合布线项目管理系统 - 生产环境种子数据
-- ============================================================================
-- 本脚本在 01-schema.sql 之后自动执行（Docker MySQL initdb.d 按文件名排序）
-- 包含：部门、岗位、角色、权限、角色-权限映射、管理员账号、字典、系统配置
-- 不包含：项目、任务、文档等业务数据（全新系统为空）
-- ============================================================================
-- 管理员账号：admin / admin123
-- 首次部署后请立即修改管理员密码！
-- ============================================================================

USE `hbsz_idc_system`;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、部门数据
-- ============================================================================
INSERT INTO `sys_department` (`id`, `name`, `parent_id`, `sort`, `leader`, `phone`, `status`) VALUES
(1,  '总公司',     NULL, 1,  '系统管理员', '010-88888001', 1),
(2,  '工程管理部', 1,    1,  '张明',       '010-88888002', 1),
(3,  '技术研发部', 1,    2,  '李华',       '010-88888003', 1),
(4,  '质量安全部', 1,    3,  '王强',       '010-88888004', 1),
(5,  '市场商务部', 1,    4,  '赵磊',       '010-88888005', 1),
(6,  '综合办公室', 1,    5,  '',           '',             1),
(7,  '第一工程处', 2,    1,  '',           '',             1),
(8,  '第二工程处', 2,    2,  '',           '',             1),
(9,  'BIM技术中心',3,    1,  '',           '',             1);

-- ============================================================================
-- 二、岗位数据
-- ============================================================================
INSERT INTO `sys_position` (`id`, `code`, `name`, `level`, `description`, `status`) VALUES
(1, 'gm',               '总经理',     1,  '公司最高管理者',     1),
(2, 'pm',               '项目经理',   2,  '项目全权负责人',     1),
(3, 'senior_engineer',  '高级工程师', 3,  '资深技术骨干',       1),
(4, 'engineer',         '工程师',     4,  '一般技术人员',       1),
(5, 'junior_engineer',  '初级工程师', 5,  '刚入职技术人员',     1),
(6, 'outsourcing',      '外包人员',   6,  '外部协作用户',       1),
(7, 'viewer',           '只读用户',   9,  '仅查看无编辑权限',   1);

-- ============================================================================
-- 三、角色数据
-- ============================================================================
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `sort`, `status`) VALUES
(1, '系统管理员',  'admin',           '拥有系统全部权限，可管理用户、角色、权限、配置及所有业务数据',     1, 1),
(2, '项目经理',    'project_manager', '负责项目管理全流程，可管理项目、文档、任务、进度填报及统计查询', 2, 1),
(3, '工程师',      'engineer',        '执行具体任务，可查询项目、上传文档、查询任务并填报进度',         3, 1),
(4, '普通用户',    'user',            '只读用户，仅可查询项目、文档、任务信息',                         4, 1);

-- ============================================================================
-- 四、权限数据
-- ============================================================================
INSERT INTO `sys_permission` (`id`, `parent_id`, `permission_name`, `permission_code`, `permission_type`, `path`, `component`, `icon`, `sort`, `is_visible`, `status`, `remark`) VALUES
-- 系统管理
(1,  NULL,  '系统管理',     'system',               1, '/system',             NULL,                   'Setting',      90,  1, 1, '系统管理模块'),
(10, 1,  '用户管理',     'system:user',          1, '/system/users',      NULL,                   'User',         1,   1, 1, '用户管理菜单'),
(11, 10, '用户查询',     'system:user:query',    2, '',                   NULL,                   '',             1,   1, 1, '查询用户列表'),
(12, 10, '用户新增',     'system:user:add',      2, '',                   NULL,                   '',             2,   0, 1, '新增用户'),
(13, 10, '用户编辑',     'system:user:edit',     2, '',                   NULL,                   '',             3,   0, 1, '编辑用户'),
(14, 10, '用户删除',     'system:user:remove',   2, '',                   NULL,                   '',             4,   0, 1, '删除用户'),
(15, 10, '密码重置',     'system:user:reset',    2, '',                   NULL,                   '',             5,   0, 1, '重置用户密码'),
(16, 10, '状态切换',     'system:user:status',   2, '',                   NULL,                   '',             6,   0, 1, '启用/禁用用户'),
(20, 1,  '角色管理',     'system:role',          1, '/system/roles',      NULL,                   'UserFilled',   2,   1, 1, '角色管理菜单'),
(21, 20, '角色查询',     'system:role:query',    2, '',                   NULL,                   '',             1,   1, 1, '查询角色列表'),
(22, 20, '角色新增',     'system:role:add',      2, '',                   NULL,                   '',             2,   0, 1, '新增角色'),
(23, 20, '角色编辑',     'system:role:edit',     2, '',                   NULL,                   '',             3,   0, 1, '编辑角色'),
(24, 20, '角色删除',     'system:role:remove',   2, '',                   NULL,                   '',             4,   0, 1, '删除角色'),
(25, 20, '权限分配',     'system:role:permission',2,'',                   NULL,                   '',             5,   0, 1, '分配角色权限'),
(30, 1,  '权限管理',     'system:permission',   1, '/system/permissions', NULL,                   'Key',          3,   1, 1, '权限管理菜单'),
(31, 30, '权限查询',     'system:permission:query',2,'',                  NULL,                   '',             1,   1, 1, '查询权限树'),
(32, 30, '权限新增',     'system:permission:add',2, '',                   NULL,                   '',             2,   0, 1, '新增权限'),
(33, 30, '权限编辑',     'system:permission:edit',2,'',                   NULL,                   '',             3,   0, 1, '编辑权限'),
(34, 30, '权限删除',     'system:permission:remove',2,'',                NULL,                   '',             4,   0, 1, '删除权限'),
(66, 1,  '岗位管理',     'position:list',        1, '/system/positions',  'system/PositionList',   'Postcard',     5,   1, 1, '岗位管理-列表'),
(67, 66, '岗位查询',     'position:query',       2, '',                   NULL,                   '',             6,   0, 1, '岗位管理-查询'),
(68, 66, '新增岗位',     'position:add',         2, '',                   NULL,                   '',             7,   0, 1, '岗位管理-新增'),
(69, 66, '编辑岗位',     'position:edit',        2, '',                   NULL,                   '',             8,   0, 1, '岗位管理-编辑'),
(70, 66, '删除岗位',     'position:remove',      2, '',                   NULL,                   '',             9,   0, 1, '岗位管理-删除'),
-- 工作台
(74, NULL, '工作台',       'dashboard',            1, '/dashboard',          NULL,                   'Odometer',     1,   1, 1, '工作台首页（无需权限）'),
(76, NULL, '系统设置',     'system:settings',      1, '/settings',           NULL,                   'Setting',       100, 1, 1, '个人/系统设置（无需权限）'),
-- 项目管理
(35, NULL, '项目管理',     'project',              1, '/projects',           NULL,                   'Folder',       2,   1, 1, '项目管理模块'),
(40, 35, '项目查询',     'project:query',        1, '/projects',           NULL,                   '',             1,   1, 1, '查询项目列表'),
(41, 35, '项目新增',     'project:add',          2, '',                   NULL,                   '',             2,   0, 1, '新增项目'),
(42, 35, '项目编辑',     'project:edit',         2, '',                   NULL,                   '',             3,   0, 1, '编辑项目'),
(43, 35, '项目删除',     'project:remove',       2, '',                   NULL,                   '',             4,   0, 1, '删除项目'),
(44, 35, '项目归档',     'project:archive',      2, '',                   NULL,                   '',             5,   0, 1, '归档项目'),
(65, 35, '项目授权转移', 'project:transfer',     1, '/projects/transfer-logs','projects/TransferLogs','Share',     65,  1, 1, '项目经理将项目授权给指定工程师'),
-- 项目协作
(75, NULL, '项目协作',     'project:collab',       1, '/project-collab',     NULL,                   'Share',         3,   1, 1, '项目授权与项目组管理'),
(71, 75, '申请项目授权', 'project:auth:request', 1, '/project-auth',       NULL,                   'Connection',    1,   1, 1, '项目授权-申请'),
(72, 71, '审批项目授权', 'project:auth:approve', 2, '',                   NULL,                   '',             11,  0, 1, '项目授权-审批'),
(73, 75, '管理项目组',   'project:team:manage',  1, '/project-teams',      NULL,                   'UserFilled',    2,   1, 1, '项目组-管理'),
-- 文档管理
(36, NULL, '文档管理',     'document',             1, '/documents',          NULL,                   'Document',     4,   1, 1, '文档管理模块'),
(45, 36, '文档查询',     'document:query',       1, '/documents',          NULL,                   '',             1,   1, 1, '查询文档列表'),
(46, 36, '文档上传',     'document:add',         2, '',                   NULL,                   '',             2,   0, 1, '上传文档'),
(47, 36, '文档编辑',     'document:edit',        2, '',                   NULL,                   '',             3,   0, 1, '编辑文档'),
(48, 36, '文档删除',     'document:remove',      2, '',                   NULL,                   '',             4,   0, 1, '删除文档'),
(49, 36, '文档下载',     'document:download',    2, '',                   NULL,                   '',             5,   0, 1, '下载文档'),
(81, 36, '文档审核',     'document:review',      2, '',                   NULL,                   '',             6,   0, 1, '审核上传的文档（通过/驳回）'),
-- 进度任务
(37, NULL, '进度任务',     'task',                 1, '/progress-task',      NULL,                   'DataLine',     5,   1, 1, '任务管理模块'),
(50, 37, '进度管理',     'task:query',           1, '/progress',           NULL,                   'DataLine',     1,   1, 1, '查询任务列表'),
(51, 37, '任务新增',     'task:add',             2, '',                   NULL,                   '',             2,   0, 1, '新增任务'),
(52, 37, '任务编辑',     'task:edit',            2, '',                   NULL,                   '',             3,   0, 1, '编辑任务'),
(53, 37, '任务删除',     'task:remove',          2, '',                   NULL,                   '',             4,   0, 1, '删除任务'),
(54, 37, '进度填报',     'task:progress',        2, '',                   NULL,                   '',             5,   0, 1, '填报任务进度'),
(58, 37, '进度审核',     'task:progress:review', 1, '/review/pending',    NULL,                   'Bell',          2,   1, 1, '审核工程师提交的进度填报'),
(77, 37, '风险预警看板', 'task:risk-board',      1, '/my/risk-board',     NULL,                   'Warning',       3,   1, 1, '跨项目关键节点风险预警'),
(78, 37, '我的任务',     'task:my-tasks',        1, '/my/tasks',           NULL,                   'List',          4,   1, 1, '当前用户负责的任务'),
(79, 37, '我的填报历史', 'task:my-submissions',  1, '/my/submissions',     NULL,                   'EditPen',       5,   1, 1, '当前用户的进度填报历史'),
-- 归档管理
(38, NULL, '归档管理',     'archive',              1, '/archive',            NULL,                   'Box',           7,   1, 1, '归档管理模块'),
(55, 38, '归档查询',     'archive:query',        1, '/archive',            NULL,                   '',             1,   1, 1, '查询归档记录'),
-- 数据分析
(39, NULL, '数据分析',     'statistics',           1, '/data-analysis',      NULL,                   'DataAnalysis', 8,   1, 1, '统计报表模块'),
(56, 39, '统计查询',     'statistics:query',     1, '/statistics',          NULL,                   'TrendCharts',  1,   1, 1, '查询统计数据'),
(57, 39, '日报导出',     'statistics:export',    1, '/daily-report',       NULL,                   'Document',     2,   1, 1, '导出统计报表'),
(80, 39, '审批流程图',   'approval:flow',        1, '/approval-flow',      'ApprovalFlow',        'Share',         3,   1, 1, '审批流程图可视化'),
-- 客户管理
(60, NULL, '客户管理',     'customer',             1, '/customers',          NULL,                   'OfficeBuilding',6,  1, 1, '客户信息管理模块'),
(61, 60, '客户查询',     'customer:query',       1, '/customers',          NULL,                   '',             1,   1, 1, '查询客户列表'),
(62, 60, '客户新增',     'customer:add',         2, '',                   NULL,                   '',             2,   0, 1, '新增客户'),
(63, 60, '客户编辑',     'customer:edit',        2, '',                   NULL,                   '',             3,   0, 1, '编辑客户'),
(64, 60, '客户删除',     'customer:remove',      2, '',                   NULL,                   '',             4,   0, 1, '删除客户');

-- ============================================================================
-- 五、角色-权限映射
-- ============================================================================
-- 角色1: admin（拥有全部权限）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(1,1),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),
(1,20),(1,21),(1,22),(1,23),(1,24),(1,25),
(1,30),(1,31),(1,32),(1,33),(1,34),
(1,35),(1,36),(1,37),(1,38),(1,39),
(1,40),(1,41),(1,42),(1,43),(1,44),
(1,45),(1,46),(1,47),(1,48),(1,49),
(1,50),(1,51),(1,52),(1,53),(1,54),
(1,55),(1,56),(1,57),(1,58),
(1,60),(1,61),(1,62),(1,63),(1,64),
(1,65),(1,66),(1,67),(1,68),(1,69),(1,70),
(1,71),(1,72),(1,73),
(1,74),(1,75),(1,76),(1,77),(1,78),(1,79),(1,80),
(1,81);

-- 角色2: project_manager（项目经理）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(2,11),
(2,35),(2,36),(2,37),(2,38),(2,39),
(2,40),(2,41),(2,42),(2,43),(2,44),
(2,45),(2,46),(2,47),(2,48),(2,49),
(2,50),(2,51),(2,52),(2,53),(2,54),
(2,55),(2,56),(2,57),(2,58),
(2,60),(2,61),(2,62),(2,63),(2,64),
(2,65),(2,71),(2,72),(2,73),(2,80),
(2,81);

-- 角色3: engineer（工程师）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(3,35),(3,36),(3,37),(3,38),(3,39),
(3,40),(3,45),(3,46),(3,49),
(3,50),(3,54),(3,55),(3,56),(3,80);

-- 角色4: user（普通用户 — 只读用户，仅查询权限）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(4,35),(4,36),(4,37),(4,38),(4,39),
(4,40),(4,45),(4,49),
(4,50),(4,55),(4,56),
(4,60),(4,61),(4,80);

-- ============================================================================
-- 六、管理员账号
-- ============================================================================
-- 默认密码: admin123 (BCrypt 加密)
-- ⚠️ 首次登录后请立即修改密码！
INSERT INTO `sys_user` (`id`, `username`, `password`, `name`, `nickname`, `gender`, `email`, `phone`, `bio`, `two_factor_enabled`, `status`) VALUES
(1, 'admin', '$2a$10$bqPsyCjzshX76uZVN/lV7O6py0nWwYPtwN9Nf6M2.xHvU9SzyeZp.', '系统管理员', '超级管理员', 1, 'admin@hbsz.com', '13800138001', '系统超级管理员', 0, 1);

-- 管理员角色绑定
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- ============================================================================
-- 七、字典类型
-- ============================================================================
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `remark`) VALUES
('项目状态',   'project_status',    '项目状态选项'),
('项目优先级', 'project_priority',  '项目优先级选项'),
('项目类型',   'project_type',      '项目类型选项'),
('任务状态',   'task_status',       '任务状态选项'),
('文档分类',   'document_category', '文档分类选项'),
('文档状态',   'document_status',   '文档审核状态');

-- ============================================================================
-- 八、字典数据
-- ============================================================================
INSERT INTO `sys_dict_data` (`dict_type`, `dict_label`, `dict_value`, `css_class`, `sort`) VALUES
-- 项目状态
('project_status', '进行中',   '进行中',   'primary', 1),
('project_status', '已完成',   '已完成',   'success', 2),
('project_status', '已归档',   '已归档',   'info',    3),
('project_status', '暂停',     '暂停',     'warning', 4),
('project_status', '延期',     '延期',     'danger',  5),
-- 项目优先级
('project_priority', '高', '高', 'danger',  1),
('project_priority', '中', '中', 'warning', 2),
('project_priority', '低', '低', 'info',    3),
-- 项目类型
('project_type', '新建',   '新建',   'success', 1),
('project_type', '改造',   '改造',   'warning', 2),
('project_type', '维护',   '维护',   'info',    3),
-- 任务状态
('task_status', '未开始',   '未开始',   'info',    1),
('task_status', '进行中',   '进行中',   'primary', 2),
('task_status', '已完成',   '已完成',   'success', 3),
('task_status', '已延期',   '已延期',   'danger',  4),
('task_status', '已暂停',   '已暂停',   'warning', 5),
-- 文档分类
('document_category', '合同文件',   '合同文件',   'warning', 1),
('document_category', '技术文档',   '技术文档',   'primary', 2),
('document_category', '验收报告',   '验收报告',   'success', 3),
('document_category', '设计图纸',   '设计图纸',   'info',    4),
('document_category', '施工记录',   '施工记录',   '',        5),
('document_category', '其他',       '其他',       'info',    6),
-- 文档状态
('document_status', '待审核',   '待审核',   'warning', 1),
('document_status', '已审核',   '已审核',   'success', 2),
('document_status', '已驳回',   '已驳回',   'danger',  3);

-- ============================================================================
-- 九、系统配置
-- ============================================================================
INSERT INTO `sys_system_config` (`config_key`, `config_value`, `config_name`, `config_type`) VALUES
('system_name',          '数据中心综合布线项目管理系统', '系统名称',     'string'),
('system_version',       'V1.0.0',                      '系统版本',     'string'),
('theme_color',          '#1890ff',                     '主题色',       'string'),
('default_project_view', 'table',                       '默认项目视图', 'string'),
('default_page_size',    '20',                          '默认每页条数', 'number'),
('auto_save_enabled',    'true',                        '表单自动保存', 'boolean');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 种子数据初始化完成
-- 管理员账号: admin / admin123
-- 包含: 9个部门, 7个岗位, 4个角色, 57个权限, 120+角色权限映射, 1个管理员
-- ============================================================================
