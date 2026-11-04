<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'edit' ? '在线编辑' : '在线预览'"
    fullscreen
    class="onlyoffice-editor-dialog"
    @update:model-value="$emit('update:visible', $event)"
    @open="handleOpen"
    @closed="handleClosed"
  >
    <!-- 编辑器容器常驻（v-show）：DocEditor 初始化时 loading 尚未结束，占位 div 必须已存在；
         loading/error 以 absolute 覆盖层形式叠在上面 -->
    <div class="editor-wrapper">
      <!-- DocEditor 初始化会用 iframe 替换占位 div，外层 wrapper 必须持有固定高度，
           否则 iframe 的 height:100% 解析失败塌陷为浏览器默认 150px（显示不完整的根因） -->
      <div
        id="onlyoffice-editor"
        class="editor-container"
      />
    </div>
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

/** 动态加载 OnlyOffice DocsAPI 脚本（已加载则跳过）。
 *  serverUrl 由后端 edit-config 响应下发（ONLYOFFICE_SERVER_URL 环境变量），保证前后端同源配置 */
function loadOnlyOfficeApi(serverUrl) {
  return new Promise((resolve, reject) => {
    if (window.DocsAPI) {
      resolve()
      return
    }
    if (!serverUrl) {
      reject(new Error('OnlyOffice 服务地址未配置'))
      return
    }
    const script = document.createElement('script')
    script.src = `${serverUrl}/web-apps/apps/api/documents/api.js`
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

/** 对话框打开：获取配置 + 加载脚本 + 初始化编辑器 */
async function handleOpen() {
  if (!props.docId) return
  loading.value = true
  error.value = ''
  try {
    // request 拦截器返回完整响应包 {code,message,data}，编辑器配置在 data 字段
    const resp = await documentApi.editConfig(props.docId, props.mode)
    const config = resp.data || {}
    await loadOnlyOfficeApi(config.serverUrl)
    if (editor) {
      editor.destroyEditor()
      editor = null
    }
    // serverUrl 仅用于加载脚本，不属于 DocsAPI.DocEditor 的合法配置项，剔除后透传
    const { serverUrl: _ignored, ...editorProps } = config
    editor = new window.DocsAPI.DocEditor('onlyoffice-editor', {
      ...editorProps,
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

<style>
/* el-dialog 默认 teleport 到 body，祖先链上没有本组件的 data-v 属性，
   scoped/:deep 均无法命中 dialog 内部元素——使用全局样式并以专属 class 前缀隔离 */
.onlyoffice-editor-dialog.el-dialog.is-fullscreen {
  display: flex;
  flex-direction: column;
}

.onlyoffice-editor-dialog .el-dialog__header {
  flex-shrink: 0;
}

/* body 撑满 header 以下空间：编辑器 iframe 高度的稳定参照 */
.onlyoffice-editor-dialog .el-dialog__body {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

/* wrapper 持有固定高度；DocEditor 用 iframe 替换 #onlyoffice-editor 后仍包裹在 wrapper 内，
   iframe 的 height:100% 由此生效 */
.onlyoffice-editor-dialog .editor-wrapper,
.onlyoffice-editor-dialog .editor-container {
  width: 100%;
  height: 100%;
}

.onlyoffice-editor-dialog .editor-loading,
.onlyoffice-editor-dialog .editor-error {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #fff;
  color: #606266;
}
</style>
