/**
 * Excel 批量导入组合式函数
 *
 * P3 修复：UserManagement / CustomerManagement / ProjectList 三个视图各自
 * 重复了约 60 行完全相同的导入逻辑（状态、el-upload 回调、模板下载、导入提交、
 * 错误详情 HTML 转义）。本 composable 抽取公共部分，调用方只需提供三个 API 相关
 * 差异项，返回的绑定名与原模板引用完全一致，模板层零改动。
 *
 * 用法：
 *   const {
 *     showImportDialog, importLoading, uploadRef, importFile,
 *     handleFileChange, handleExceed, resetImportDialog,
 *     handleDownloadTemplate, handleImport
 *   } = useImport({
 *     downloadTemplate: () => userApi.downloadUserImportTemplate(),
 *     importFn: (file) => userApi.importUsers(file),
 *     templateName: 'user_import_template.xlsx',
 *     onSuccess: () => fetchList()
 *   })
 * @module composables/useImport
 */
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { feedback, escapeHtml } from '@/utils/feedback'
import { downloadBlobFromResponse } from '@/utils/download'

/**
 * @param {Object} options
 * @param {Function} options.downloadTemplate 模板下载接口，返回 axios blob 响应
 * @param {Function} options.importFn 导入接口，签名 (file: File) => Promise
 * @param {string} options.templateName 模板保存文件名（含 .xlsx 后缀）
 * @param {Function} [options.onSuccess] 导入成功后的回调（通常为刷新列表）
 * @returns {Object} 模板可直接解构使用的导入相关状态与方法
 */
export function useImport({ downloadTemplate, importFn, templateName, onSuccess }) {
  /** 导入对话框显隐 */
  const showImportDialog = ref(false)
  /** 导入提交中标记（按钮 loading） */
  const importLoading = ref(false)
  /** el-upload 实例引用 */
  const uploadRef = ref()
  /** 待导入的原始文件 */
  const importFile = ref(null)

  /** 文件选择回调 @param {Object} file el-upload 文件对象 */
  function handleFileChange(file) {
    importFile.value = file.raw
  }

  /** 超出上传数量限制回调 */
  function handleExceed() {
    feedback.warning('只能上传一个文件')
  }

  /** 对话框关闭时清空文件选择 */
  function resetImportDialog() {
    importFile.value = null
    if (uploadRef.value) uploadRef.value.clearFiles()
  }

  /** 下载导入模板（xlsx） */
  async function handleDownloadTemplate() {
    try {
      const response = await downloadTemplate()
      downloadBlobFromResponse(response, templateName)
    } catch (e) {
      feedback.error(e.message || '下载模板失败')
    }
  }

  /** 确认导入：上传文件并显示结果 */
  async function handleImport() {
    if (!importFile.value) {
      feedback.warning('请先选择文件')
      return
    }
    importLoading.value = true
    try {
      const res = await importFn(importFile.value)
      const result = res.data || res
      feedback.success(`导入完成：成功 ${result.successCount ?? 0} 条，失败 ${result.failCount ?? 0} 条`)
      if (result.errors && result.errors.length > 0) {
        // 后端逐条错误可能含用户可控内容（姓名/手机号等），必须转义后才能安全用于 HTML 渲染
        ElMessageBox.alert(
          result.errors.map(e => escapeHtml(e)).join('<br>'),
          '导入失败详情',
          { dangerouslyUseHTMLString: true, type: 'warning' }
        )
      }
      showImportDialog.value = false
      if (typeof onSuccess === 'function') onSuccess()
    } catch (e) {
      feedback.error(e.message || '导入失败')
    } finally {
      importLoading.value = false
    }
  }

  return {
    showImportDialog,
    importLoading,
    uploadRef,
    importFile,
    handleFileChange,
    handleExceed,
    resetImportDialog,
    handleDownloadTemplate,
    handleImport
  }
}
