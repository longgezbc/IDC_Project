-- V20260822 项目经理用户关联回填
--
-- 背景：biz_project.manager_name 为自由文本，创建/导入时若前端未显式选择用户（managerId），
-- manager_id 保持 NULL，导致以下链路对该项目全部静默失效：
--   1. 进度填报审核通知（TaskServiceImpl.notifyProgressSubmitted）
--   2. 工作量填报审核通知（WorkloadServiceImpl.notifyWorkloadSubmitted）
--   3. 每日待办摘要（ApprovalTodoMapper.countPendingByManager 过滤 manager_id IS NOT NULL）
--   4. 审批中心非管理员数据归属
--
-- 策略：按 manager_name 与启用状态（status=1）用户姓名唯一匹配回填；
--       重名（同名多用户）不回填，避免错关联，由人工在项目编辑中指定。
-- 幂等：WHERE manager_id IS NULL 保证重复执行无副作用（Flyway 重放/手动补跑安全）。
UPDATE biz_project p
JOIN (
    SELECT name, MIN(id) AS uid
    FROM sys_user
    WHERE status = 1
    GROUP BY name
    HAVING COUNT(*) = 1
) u ON u.name = p.manager_name
SET p.manager_id = u.uid
WHERE p.manager_id IS NULL
  AND p.manager_name IS NOT NULL;
