/**
 * 系统常量定义
 * 集中管理下拉选项等常量数据
 *
 * 供表单选择器、筛选条件等复用，保证各页面选项取值一致。
 * @module constants/options
 */

// 项目相关选项
export const projectStatusOptions = ['进行中', '已完成', '已归档', '暂停', '延期']
export const projectPriorityOptions = ['高', '中', '低']
export const projectTypeOptions = ['新建', '改造', '维护']

// 文档相关选项
export const documentCategoryOptions = ['合同文件', '技术文档', '验收报告', '设计图纸', '施工记录', '其他']
export const documentStatusOptions = ['待审核', '已审核', '已驳回']

// 任务相关选项
export const taskStatusOptions = ['未开始', '进行中', '已完成', '已延期', '已暂停']
