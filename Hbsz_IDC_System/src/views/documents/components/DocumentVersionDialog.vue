<template>
  <!--
    版本管理对话框组件（P1-2.1 拆分自 DocumentManagement.vue）
    父组件通过 v-model:visible 控制显隐，通过 :doc 传入当前文档；
    对话框自动拉取历史版本列表；上传新版本或下载历史版本均触发 success 事件，
    由父组件刷新外层列表。
  -->
  <el-dialog
    :model-value="visible"
    title="版本管理"
    width="680px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div
      v-if="doc"
      class="version-container"
    >
      <div class="version-header">
        <div class="version-doc-name">
          {{ doc.name }}
        </div>
        <div class="version-current">
          当前版本：v{{ doc.version }}
        </div>
      </div>
      <el-divider content-position="left">
        历史版本
      </el-divider>
      <el-table
        v-loading="versionLoading"
        :data="versionList"
        stripe
      >
        <el-table-column
          type="index"
          label="#"
          width="60"
        />
        <el-table-column
          prop="version"
          label="版本号"
          width="120"
        >
          <template #default="{ row }">
            <el-tag :type="row.isCurrent ? 'success' : 'info'">
              v{{ row.version }}
            </el-tag>
            <el-tag
              v-if="row.isCurrent"
              size="small"
              type="success"
              style="margin-left: 6px"
            >
              当前
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="remark"
          label="版本说明"
          min-width="180"
        />
        <el-table-column
          prop="uploader"
          label="上传人"
          width="100"
        />
        <el-table-column
          prop="createdAt"
          label="上传时间"
          width="160"
        />
        <el-table-column
          label="操作"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <BaseButton
              variant="link"
              size="small"
              icon-name="Download"
              @click="handleDownload(row)"
            >
              下载
            </BaseButton>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!versionLoading && versionList.length === 0"
        description="暂无历史版本"
        :image-size="60"
      />
      <el-divider content-position="left">
        上传新版本
      </el-divider>
      <el-form
        :model="versionForm"
        label-width="90px"
      >
        <el-form-item
          label="版本号"
          required
        >
          <el-input
            v-model="versionForm.version"
            placeholder="例如：1.0.1"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input
            v-model="versionForm.remark"
            type="textarea"
            :rows="2"
            placeholder="本次版本更新说明"
          />
        </el-form-item>
        <el-form-item
          label="选择文件"
          required
        >
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            :on-change="handleVersionFileChange"
            :on-remove="() => versionFile = null"
          >
            <el-icon class="el-icon--upload">
              <UploadFilled />
            </el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <BaseButton
            variant="primary"
            icon-name="Upload"
            :loading="versionUploading"
            @click="confirmAddVersion"
          >
            上传新版本
          </BaseButton>
        </el-form-item>
      </el-form>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 版本管理对话框
 * 业务用途：查看文档历史版本、下载指定版本、上传新版本（带版本号与说明）。
 * 数据来源：documentStore.fetchDetail（用于获取 versions 列表）、documentStore.addVersion、
 *          documentStore.downloadDocument。
 */
import { ref, watch } from 'vue'
import { feedback } from '@/utils/feedback'
import { UploadFilled } from '@element-plus/icons-vue'
import { useDocumentStore } from '@/stores/document'
import { downloadBlob } from '@/utils/download'
// 注：BaseButton 已在 main.js 全局注册，无需在此导入

const props = defineProps({
  /** 对话框显隐（v-model:visible） */
  visible: { type: Boolean, default: false },
  /** 当前文档对象（包含 id、name、version） */
  doc: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'success'])

const documentStore = useDocumentStore()

const versionList = ref([])
const versionLoading = ref(false)
const versionForm = ref({ version: '', remark: '' })
const versionFile = ref(null)
const versionUploading = ref(false)

/** 对话框打开且文档存在时拉取历史版本列表 */
watch(() => [props.visible, props.doc], ([val, doc]) => {
  if (val && doc) {
    versionList.value = []
    versionForm.value = { version: '', remark: '' }
    versionFile.value = null
    fetchVersionList()
  }
}, { immediate: true })

/** 拉取历史版本列表（通过 fetchDetail 获取 versions 字段并标记当前版本） */
async function fetchVersionList() {
  if (!props.doc) return
  versionLoading.value = true
  try {
    await documentStore.fetchDetail(props.doc.id)
    const detail = documentStore.currentDocument
    const versions = (detail && detail.versions) || []
    versionList.value = versions.map(v => ({
      ...v,
      isCurrent: v.version === props.doc.version
    }))
  } catch (error) {
    console.error('加载版本列表失败:', error)
    versionList.value = []
  } finally {
    versionLoading.value = false
  }
}

/** 版本上传文件选择 */
function handleVersionFileChange(file) {
  versionFile.value = file.raw
}

/** 确认上传新版本 */
async function confirmAddVersion() {
  if (!props.doc) return
  if (!versionForm.value.version) {
    feedback.warning('请输入版本号')
    return
  }
  if (!versionFile.value) {
    feedback.warning('请选择要上传的文件')
    return
  }
  versionUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', versionFile.value)
    formData.append('version', versionForm.value.version)
    if (versionForm.value.remark) {
      formData.append('remark', versionForm.value.remark)
    }
    await documentStore.addVersion(props.doc.id, formData)
    // 刷新版本列表与外层文档列表
    await fetchVersionList()
    emit('success')
    // 重置表单
    versionForm.value = { version: '', remark: '' }
    versionFile.value = null
  } catch (error) {
    console.error('上传新版本失败:', error)
  } finally {
    versionUploading.value = false
  }
}

/** 下载指定版本（包括当前版本） */
async function handleDownload(row) {
  try {
    const res = await documentStore.downloadDocument(row.id)
    downloadBlob(res.data, row.name || `${props.doc.name}_v${row.version}`)
  } catch (error) {
    console.error('下载失败:', error)
    feedback.error('下载失败，请稍后重试')
  }
}
</script>

<style scoped>
.version-container {
  padding: 0 8px;
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.version-doc-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.version-current {
  font-size: 13px;
  color: #1890ff;
  font-weight: 500;
}
</style>
