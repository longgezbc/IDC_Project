<template>
  <!--
    进度填报对话框（P1-2.1 拆分自 ProjectProgress.vue）
    父组件通过 :visible 控制显隐，:tasks 传入当前项目任务列表（供任务下拉选择），
    :preselect-task 传入预选任务（点击列表"填报"按钮时），或 null（点击顶部"进度填报"按钮时）。
    提交成功后 emit('success')，父组件刷新任务列表与待审核计数。
  -->
  <el-dialog
    :model-value="visible"
    title="进度填报"
    width="640px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-alert
      title="提交后需项目经理审核通过才会更新任务进度"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
    <el-form
      :model="reportForm"
      label-width="100px"
    >
      <el-form-item label="选择任务">
        <el-select
          v-model="reportForm.taskId"
          placeholder="请选择任务"
          style="width: 100%"
          @change="onReportTaskChange"
        >
          <el-option
            v-for="t in tasks.filter(t => t.status !== '已完成')"
            :key="t.id"
            :label="t.name + ' (' + t.progress + '%)'"
            :value="t.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="当前进度">
        <el-slider
          v-model="reportForm.progress"
          :max="100"
          show-input
          aria-label="任务进度百分比"
        />
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item
            label="实际开工时间"
            required
          >
            <el-date-picker
              v-model="reportForm.actualStartDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择实际开工时间"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            label="实际结束时间"
            required
          >
            <el-date-picker
              v-model="reportForm.actualEndDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择实际结束时间"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="施工图片">
        <el-upload
          v-model:file-list="reportForm.imageList"
          action="/api/files/upload"
          :headers="uploadHeaders"
          list-type="picture-card"
          :before-upload="beforeImageUpload"
          :on-success="onUploadSuccess"
          :on-remove="onUploadRemove"
          :on-preview="onImagePreview"
          accept="image/*"
          multiple
          :limit="6"
        >
          <el-icon><Plus /></el-icon>
          <template #tip>
            <div class="upload-tip">
              最多上传 6 张施工图片，单张不超过 10MB，支持 JPG/PNG/GIF
            </div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="填报说明">
        <el-input
          v-model="reportForm.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入进度说明（可选）"
        />
      </el-form-item>
      <el-form-item label="任务状态">
        <el-tag>{{ reportForm.progress === 100 ? '已完成' : reportForm.progress > 0 ? '进行中' : '未开始' }}</el-tag>
      </el-form-item>
    </el-form>
    <template #footer>
      <BaseButton @click="$emit('update:visible', false)">
        取消
      </BaseButton>
      <BaseButton
        variant="primary"
        :loading="reportSubmitting"
        @click="handleReport"
      >
        提交审核
      </BaseButton>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 进度填报对话框
 *
 * 业务用途：工程师/施工人员对任务进行进度填报，提交后由项目经理审核。
 *          需上传施工图片、填写实际开工/结束时间，提交后状态为"待审核"。
 * 数据来源：tasks 由父组件传入（当前项目的任务列表），preselectTask 为预选任务或 null。
 * 副作用：提交成功后调用 progressStore.reportProgress，并 emit('success') 通知父组件刷新。
 *
 * P1-2.1：从 ProjectProgress.vue 抽取，含图片上传、表单校验与提交逻辑。
 */
// 注：BaseButton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, computed, watch } from 'vue'
import { useProgressStore } from '@/stores/progress'
import { useUserStore } from '@/stores/user'
import { feedback } from '@/utils/feedback'
import { Plus } from '@element-plus/icons-vue'

const props = defineProps({
  /** 对话框显隐 */
  visible: { type: Boolean, default: false },
  /** 当前项目任务列表（供任务下拉选择，过滤掉已完成任务） */
  tasks: { type: Array, default: () => [] },
  /** 预选任务对象（点击列表"填报"按钮时传入），null 表示不预选 */
  preselectTask: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'success'])

const progressStore = useProgressStore()
const userStore = useUserStore()

// 上传请求头：携带 JWT Token（用于 el-upload 直传后端 /api/files/upload）
const uploadHeaders = computed(() => ({
  Authorization: userStore.token ? `Bearer ${userStore.token}` : ''
}))

const reportSubmitting = ref(false)

// 进度填报表单模型：含进度、备注、施工图片、实际开工/结束时间
const reportForm = reactive({
  taskId: '',
  progress: 0,
  remark: '',
  images: [],         // 已上传的图片相对路径数组（提交时回传给后端）
  imageList: [],       // el-upload file-list 双向绑定（含 uid/name/url 等）
  actualStartDate: null,  // 实际开工时间（必填）
  actualEndDate: null     // 实际结束时间（必填）
})

/**
 * 监听对话框显隐：打开时根据 preselectTask 回填或重置表单
 */
watch(() => props.visible, (val) => {
  if (!val) return
  if (props.preselectTask) {
    reportForm.taskId = props.preselectTask.id
    reportForm.progress = props.preselectTask.progress
    reportForm.remark = ''
    reportForm.images = []
    reportForm.imageList = []
    reportForm.actualStartDate = props.preselectTask.actualStartDate || null
    reportForm.actualEndDate = props.preselectTask.actualEndDate || null
  } else {
    reportForm.taskId = ''
    reportForm.progress = 0
    reportForm.remark = ''
    reportForm.images = []
    reportForm.imageList = []
    reportForm.actualStartDate = null
    reportForm.actualEndDate = null
  }
})

/** 填报对话框内切换任务时，回填该任务当前进度 @param {number|string} taskId 任务 ID */
function onReportTaskChange(taskId) {
  const task = props.tasks.find(t => String(t.id) === String(taskId))
  if (task) {
    reportForm.progress = task.progress
  }
}

/**
 * 图片上传前校验：限制类型与大小
 * @param {File} file 待上传的文件
 */
function beforeImageUpload(file) {
  const isImage = /^image\/(jpe?g|png|gif|bmp)$/.test(file.type)
  if (!isImage) {
    feedback.error('仅支持 JPG/PNG/GIF/BMP 格式图片')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    feedback.error('单张图片大小不能超过 10MB')
    return false
  }
  return true
}

/**
 * 图片上传成功回调：将后端返回的相对路径加入 images 列表
 * @param {Object} response 后端返回的 { code, data: { url, previewUrl } }
 */
function onUploadSuccess(response) {
  if (response && response.code === 200 && response.data && response.data.url) {
    reportForm.images.push(response.data.url)
  } else {
    feedback.error('图片上传失败：' + (response?.message || '未知错误'))
  }
}

/**
 * 图片移除回调：同步从 images 列表移除
 * @param {Object} file 被移除的文件对象
 */
function onUploadRemove(file) {
  // el-upload file 与后端返回的 url 无直接关联，此处通过文件 uid 与 imageList 中匹配
  // 由于 onUploadSuccess 时已加入 images，移除时无法精确知道对应哪个 url，
  // 这里采用 file.response.data.url 进行匹配
  const url = file?.response?.data?.url
  if (url) {
    const idx = reportForm.images.indexOf(url)
    if (idx >= 0) reportForm.images.splice(idx, 1)
  }
}

/** 图片列表预览（点击已上传图片时触发） @param {Object} file 文件对象 */
async function onImagePreview(file) {
  // 通过 fetch 携带 token 获取图片，避免 window.open 未携带 Authorization
  const previewUrl = file?.response?.data?.previewUrl
  if (!previewUrl) return
  try {
    // 修复 P0：原 cryptoStorage.getItem('token') 键名错误（正确键名为 'hbsz_token'），
    // 且直接读 storage 无法获取 token 刷新后的最新值，导致所有预览请求 401。
    // 改用 userStore.token，与上传请求头（uploadHeaders）保持一致，确保拿到最新令牌。
    const token = userStore.token
    const resp = await fetch(previewUrl, { headers: { Authorization: `Bearer ${token}` } })
    if (!resp.ok) throw new Error(resp.statusText)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    // P2 修复：blob URL 创建后从不释放造成内存泄漏。新标签页加载图片后 URL 即可
    // 安全释放；延迟 60s 兜底慢网络下的图片加载，之后回收 Blob 底层内存
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    feedback.error('图片预览失败')
  }
}

/** 提交进度填报：校验已选任务与实际时间后调用填报接口，成功后通知父组件刷新 */
async function handleReport() {
  if (!reportForm.taskId) {
    feedback.warning('请选择任务')
    return
  }
  if (!reportForm.actualStartDate) {
    feedback.warning('请选择实际开工时间')
    return
  }
  if (!reportForm.actualEndDate) {
    feedback.warning('请选择实际结束时间')
    return
  }
  if (reportForm.actualStartDate > reportForm.actualEndDate) {
    feedback.warning('实际开工时间不能晚于实际结束时间')
    return
  }
  reportSubmitting.value = true
  try {
    await progressStore.reportProgress(reportForm.taskId, {
      progress: reportForm.progress,
      remark: reportForm.remark,
      images: reportForm.images,
      actualStartDate: reportForm.actualStartDate,
      actualEndDate: reportForm.actualEndDate
    })
    emit('success')
    emit('update:visible', false)
  } catch (error) {
    console.error('进度填报失败:', error)
  } finally {
    reportSubmitting.value = false
  }
}
</script>

<style scoped>
.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
