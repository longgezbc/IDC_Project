<template>
  <!--
    任务编辑对话框（P1-2.1 拆分自 ProjectProgress.vue）
    父组件通过 :visible 控制显隐，:task 传入待编辑任务（null 表示新增），
    :team-members 传入项目组成员供负责人下拉选择，:project-start-date 用于新增任务的默认开始日期。
    保存成功后 emit('success')，父组件刷新任务列表；本组件负责调用 progressStore.addTask/updateTask。
  -->
  <el-dialog
    :model-value="visible"
    :title="task && task.id ? '编辑任务' : '新增任务'"
    width="650px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form
      ref="taskFormRef"
      :model="editingTask"
      :rules="taskRules"
      label-width="100px"
    >
      <el-form-item
        label="任务名称"
        prop="name"
      >
        <el-input
          v-model="editingTask.name"
          placeholder="请输入任务名称"
        />
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item
            label="开始日期"
            prop="startDate"
          >
            <el-date-picker
              v-model="editingTask.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            label="结束日期"
            prop="endDate"
          >
            <el-date-picker
              v-model="editingTask.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item
            label="负责人"
            prop="assigneeId"
          >
            <el-select
              v-model="editingTask.assigneeId"
              placeholder="请选择负责人"
              filterable
              style="width: 100%"
              @change="handleAssigneeChange"
            >
              <el-option
                v-for="m in teamMembers"
                :key="m.userId"
                :label="(m.userName || m.userUsername) + (m.positionName ? ' (' + m.positionName + ')' : '')"
                :value="m.userId"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item
        label="进度"
        prop="progress"
      >
        <el-slider
          v-model="editingTask.progress"
          :max="100"
          show-input
          style="width: 100%"
          aria-label="任务进度百分比"
        />
      </el-form-item>
      <el-form-item label="任务状态">
        <el-select
          v-model="editingTask.status"
          style="width: 100%"
        >
          <el-option
            v-for="s in taskStatusOptions"
            :key="s"
            :label="s"
            :value="s"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="节点类型">
        <el-checkbox v-model="editingTask.isKey">
          关键节点
        </el-checkbox>
        <el-checkbox v-model="editingTask.isMilestone">
          里程碑
        </el-checkbox>
      </el-form-item>
      <el-form-item label="任务描述">
        <el-input
          v-model="editingTask.description"
          type="textarea"
          :rows="2"
          placeholder="请输入任务描述"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="editingTask.remark"
          type="textarea"
          :rows="2"
          placeholder="请输入备注"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <BaseButton @click="$emit('update:visible', false)">
        取消
      </BaseButton>
      <!-- 修复 H10: 绑定 taskSubmitLoading 防止重复点击提交 -->
      <BaseButton
        variant="primary"
        :loading="taskSubmitLoading"
        @click="handleSaveTask"
      >
        保存
      </BaseButton>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 任务编辑对话框（新增/编辑共用）
 *
 * 业务用途：在项目进度页新增或编辑任务，含名称、工期、负责人、进度、状态、节点类型等字段。
 * 数据来源：teamMembers 由父组件传入（项目组成员），task 为待编辑任务或 null。
 * 副作用：保存成功后调用 progressStore.addTask/updateTask，并 emit('success') 通知父组件刷新列表。
 *
 * P1-2.1：从 ProjectProgress.vue 抽取，表单状态与校验逻辑内聚于此组件。
 */
// 注：BaseButton 已在 main.js 全局注册，无需在此导入
import { ref, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useProgressStore } from '@/stores/progress'
import { taskStatusOptions } from '@/constants/options'

const props = defineProps({
  /** 对话框显隐 */
  visible: { type: Boolean, default: false },
  /** 待编辑任务对象；传 null 表示新增 */
  task: { type: Object, default: null },
  /** 项目组成员列表（用于负责人下拉选择） */
  teamMembers: { type: Array, default: () => [] },
  /** 项目开始日期（新增任务时作为默认开始日期） */
  projectStartDate: { type: String, default: '' }
})

const emit = defineEmits(['update:visible', 'success'])

const route = useRoute()
const progressStore = useProgressStore()

const taskFormRef = ref()
// 修复 H10: 新增任务保存的 loading 状态，防止重复点击提交
const taskSubmitLoading = ref(false)

// 任务编辑表单模型（新增/编辑共用）
const editingTask = reactive({
  id: null,
  name: '',
  startDate: '',
  endDate: '',
  assigneeId: null,
  assignee: '',
  progress: 0,
  status: '未开始',
  isKey: false,
  isMilestone: false,
  description: '',
  remark: ''
})

// 任务表单校验规则
const taskRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  assigneeId: [{ required: true, message: '请选择负责人', trigger: 'change' }]
}

/**
 * 监听对话框显隐：打开时根据 task prop 回填或重置表单
 * 使用 visible 而非 task 作为 watch 源，确保同一任务连续编辑时表单也会重置。
 */
watch(() => props.visible, (val) => {
  if (!val) return
  if (props.task) {
    Object.assign(editingTask, props.task)
    // 兼容旧数据：assigneeId 可能为 null（仅 assignee 字符串），下拉框会显示空
    if (editingTask.assigneeId === undefined) {
      editingTask.assigneeId = null
    }
  } else {
    Object.assign(editingTask, {
      id: null,
      name: '',
      startDate: props.projectStartDate || '',
      endDate: '',
      assigneeId: null,
      assignee: '',
      progress: 0,
      status: '未开始',
      isKey: false,
      isMilestone: false,
      description: '',
      remark: ''
    })
  }
})

/**
 * 任务负责人变更回调
 * 选择项目组成员后，同步更新 assignee 文本字段（保存到后端的 assigneeName 列）
 * @param {number|string} memberUserId 选中的成员 userId
 */
function handleAssigneeChange(memberUserId) {
  const member = props.teamMembers.find(m => m.userId === memberUserId)
  editingTask.assignee = member ? (member.userName || member.userUsername || '') : ''
}

/**
 * 保存任务：校验通过后按进度自动推断状态，再调用新增或更新接口，成功后通知父组件刷新并关闭对话框
 *
 * 修复 H10: 原代码 taskFormRef.value.validate(async valid => {...}) 缺少 await，
 * 且 Element Plus 的 validate(callback) 不会 await callback 内的 Promise，
 * 导致无 loading 状态、无法防止重复提交。改为 try-await 模式并新增 taskSubmitLoading。
 */
async function handleSaveTask() {
  try {
    await taskFormRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  if (editingTask.progress === 100) editingTask.status = '已完成'
  else if (editingTask.progress > 0) editingTask.status = '进行中'
  else editingTask.status = '未开始'

  taskSubmitLoading.value = true
  try {
    const payload = { ...editingTask, projectId: route.params.projectId }
    if (editingTask.id) {
      await progressStore.updateTask(editingTask.id, payload)
    } else {
      await progressStore.addTask(payload)
    }
    emit('success')
    emit('update:visible', false)
  } catch (e) {
    console.error('保存任务失败', e)
  } finally {
    taskSubmitLoading.value = false
  }
}
</script>
