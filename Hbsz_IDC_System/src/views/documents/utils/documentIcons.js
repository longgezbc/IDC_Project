/**
 * 文档图标工具函数（共享模块）
 *
 * P1-2.1：从 DocumentManagement.vue 抽取，供父组件及 DocumentPreviewDialog 复用。
 * 避免在多处复制粘贴文件类型 → 图标组件 / 样式类的映射表。
 * @module views/documents/utils/documentIcons
 */
import {
  Document, DocumentCopy, Tickets, Notebook, Picture, Files
} from '@element-plus/icons-vue'

/** 文件类型 → Element Plus 图标组件 映射表 */
const ICON_COMPONENT_MAP = {
  'PDF': Document,
  'DOC': DocumentCopy,
  'DOCX': DocumentCopy,
  'XLS': Tickets,
  'XLSX': Tickets,
  'PPT': Notebook,
  'PPTX': Notebook,
  'JPG': Picture,
  'PNG': Picture,
  'DWG': Document,
  'ZIP': Files
}

/** 文件类型 → 图标样式类 映射表（决定图标底色） */
const ICON_CLASS_MAP = {
  'PDF': 'icon-pdf',
  'DOC': 'icon-word',
  'DOCX': 'icon-word',
  'XLS': 'icon-excel',
  'XLSX': 'icon-excel',
  'PPT': 'icon-ppt',
  'PPTX': 'icon-ppt',
  'JPG': 'icon-image',
  'PNG': 'icon-image',
  'DWG': 'icon-cad',
  'ZIP': 'icon-zip'
}

/**
 * 根据文件类型返回对应的图标组件
 * @param {string} fileType 文件类型（如 PDF/DOC）
 * @returns {Component} Element Plus 图标组件
 */
export function getIconComponent(fileType) {
  return ICON_COMPONENT_MAP[fileType] || Document
}

/**
 * 根据文件类型返回对应的图标样式类（决定图标底色）
 * @param {string} fileType 文件类型
 * @returns {string} CSS 类名
 */
export function getIconClass(fileType) {
  return ICON_CLASS_MAP[fileType] || 'icon-default'
}
