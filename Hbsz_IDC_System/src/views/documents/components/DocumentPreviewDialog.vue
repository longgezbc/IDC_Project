<template>
  <!--
    文档预览对话框组件（P1-2.1 拆分自 DocumentManagement.vue）
    父组件通过 v-model:visible 控制显隐，通过 :doc 传入待预览文档；
    下载按钮触发 download 事件，由父组件调用下载逻辑（保留对 documentStore 的访问）。
  -->
  <el-dialog
    :model-value="visible"
    title="文档预览"
    width="800px"
    top="5vh"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div
      v-if="doc"
      class="preview-container"
    >
      <div class="preview-header">
        <div
          class="preview-icon"
          :class="getIconClass(doc.fileType)"
        >
          <el-icon :size="48">
            <component :is="getIconComponent(doc.fileType)" />
          </el-icon>
        </div>
        <div class="preview-info">
          <h3>{{ doc.name }}</h3>
          <div class="preview-meta">
            <el-tag
              size="small"
              :type="getCategoryTagType(doc.category)"
            >
              {{ doc.category }}
            </el-tag>
            <span>版本: v{{ doc.version }}</span>
            <span>大小: {{ doc.size || '-' }}</span>
            <span>上传人: {{ doc.uploader }}</span>
            <span>上传时间: {{ doc.uploadDate }}</span>
          </div>
        </div>
      </div>
      <el-divider />
      <div class="preview-body">
        <el-empty description="该文件类型暂不支持在线预览，请下载后查看">
          <BaseButton
            variant="primary"
            icon-name="Download"
            @click="handleDownload"
          >
            下载文件
          </BaseButton>
        </el-empty>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 文档预览对话框
 * 业务用途：展示文档元信息（名称/分类/版本/大小/上传人/上传时间），
 *          提供下载入口。当前不支持在线预览文件内容。
 */
import { getCategoryTagType } from '@/utils/format'
import { getIconClass, getIconComponent } from '../utils/documentIcons'
// 注：BaseButton 已在 main.js 全局注册，无需在此导入

defineProps({
  /** 对话框显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 待预览的文档对象 */
  doc: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'download'])

/** 下载按钮：交由父组件处理（保持对 documentStore.downloadDocument 的访问） */
function handleDownload() {
  emit('download')
}
</script>

<style scoped>
.preview-container {
  padding: 0 20px 20px;
}

.preview-header {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.preview-icon {
  width: 80px;
  height: 80px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.preview-info h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #6b7280;
}

.preview-body {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 图标底色（与父组件列表/网格视图保持一致的渐变风格） */
.icon-pdf { background: linear-gradient(135deg, #f5222d, #ff7875); }
.icon-word { background: linear-gradient(135deg, #1890ff, #40a9ff); }
.icon-excel { background: linear-gradient(135deg, #52c41a, #73d13d); }
.icon-ppt { background: linear-gradient(135deg, #fa541c, #ff7a45); }
.icon-image { background: linear-gradient(135deg, #722ed1, #9254de); }
.icon-cad { background: linear-gradient(135deg, #13c2c2, #36cfc9); }
.icon-zip { background: linear-gradient(135deg, #faad14, #ffc53d); }
.icon-default { background: linear-gradient(135deg, #909399, #b1b3b8); }
</style>
