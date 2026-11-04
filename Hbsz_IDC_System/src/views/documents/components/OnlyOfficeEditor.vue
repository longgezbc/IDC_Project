<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'edit' ? '在线编辑' : '在线预览'"
    fullscreen
    @update:model-value="$emit('update:visible', $event)"
    @open="handleOpen"
    @closed="handleClosed"
  >
    <div
      v-if="loading"
      class="editor-loading"
    >
      <el-icon class="is-loading">
        <Loading />
      </el-icon>
      <span>正在加载编辑器...</span>
    </div>
    <div
      v-if="error"
      class="editor-error"
    >
      <el-empty :description="error">
        <BaseButton
          variant="primary"
          @click="handleOpen"
        >
          重试
        </BaseButton>
      </el-empty>
    </div>
    <div
      id="onlyoffice-editor"
      class="editor-container"
      :style="{ visibility: loading || error ? 'hidden' : 'visible' }"
    />
  </el-dialog>
</template>

<script setup>
/**
 * OnlyOffice 在线编辑器组件
 *
 * 业务用途：在文档预览对话框中点击"在线编辑"后，全屏打开 OnlyOffice 编辑器，
 *          支持 docx/xlsx/pptx 的在线编辑与保存（保存通过后端回调自动落盘）。
 * 数据来源：调用 documentApi.editConfig 获取编辑器初始化配置（含 JWT 签名）。
 * 依赖：OnlyOffice Document Server（需单独部署，见 docker-compose.onlyoffice.yml）。
 */
import { ref, onBeforeUnmount } from 'vue'
import { documentApi } from '@/api/document'
import { Loading } from '@element-plus/icons-vue'

const props = defineProps({
  /** 对话框显隐 */
  visible: Boolean,
  /** 文档 ID */
  docId: [Number, String],
  /** 编辑模式：edit 或 view */
  mode: { type: String, default: 'edit' }
})
const emit = defineEmits(['update:visible'])

const loading = ref(false)
const error = ref('')
let editor = null

// OnlyOffice Document Server 地址（前端浏览器访问）
// 生产环境应通过系统配置或环境变量注入，MVP 暂硬编码
// 部署时改为实际地址（如 https://your-domain.com/onlyoffice）
const ONLYOFFICE_SERVER_URL = 'http://localhost:8082'

/** 动态加载 OnlyOffice DocsAPI 脚本（已加载则跳过） */
function loadOnlyOfficeApi() {
  return new Promise((resolve, reject) => {
    if (window.DocsAPI) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = `${ONLYOFFICE_SERVER_URL}/web-apps/apps/api/documents/api.js`
    script.async = true
    script.onload = () => {
      if (window.DocsAPI) {
        resolve()
      } else {
        reject(new Error('OnlyOffice API 脚本加载异常'))
      }
    }
    script.onerror = () => reject(new Error('无法加载 OnlyOffice 编辑器脚本，请确认 OnlyOffice 服务已启动'))
    document.head.appendChild(script)
  })
}

/** 对话框打开：加载脚本 + 获取配置 + 初始化编辑器 */
async function handleOpen() {
  if (!props.docId) return
  loading.value = true
  error.value = ''
  try {
    await loadOnlyOfficeApi()
    const config = await documentApi.editConfig(props.docId, props.mode)
    if (editor) {
      editor.destroyEditor()
      editor = null
    }
    editor = new window.DocsAPI.DocEditor('onlyoffice-editor', {
      ...config,
      width: '100%',
      height: '100%',
      events: {
        onError: (e) => console.error('[OnlyOffice] 错误', e)
      }
    })
  } catch (e) {
    error.value = e.message || '编辑器加载失败'
  } finally {
    loading.value = false
  }
}

/** 对话框关闭：销毁编辑器实例释放资源 */
function handleClosed() {
  if (editor) {
    editor.destroyEditor()
    editor = null
  }
  error.value = ''
}

onBeforeUnmount(() => {
  if (editor) {
    editor.destroyEditor()
    editor = null
  }
})
</script>

<style scoped>
.editor-container {
  width: 100%;
  height: calc(100vh - 120px);
}

.editor-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 8px;
  color: #606266;
}

.editor-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}
</style>
