<template>
  <div class="page-container">
    <div class="page-header">
      <div class="page-title">
        {{ isEdit ? '编辑项目' : '新建项目' }}
      </div>
      <div>
        <BaseButton @click="router.back()">
          返回
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="submitting"
          @click="handleSave"
        >
          保存
        </BaseButton>
      </div>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      class="project-form"
    >
      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            基本信息
          </div>
        </template>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item prop="name">
              <template #label>
                <el-tooltip
                  content="3-50个字符"
                  placement="top"
                >
                  <span>项目名称</span>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.name"
                placeholder="请输入项目名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="code">
              <template #label>
                <el-tooltip
                  content="系统自动生成或手动输入"
                  placement="top"
                >
                  <span>项目编号</span>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.code"
                placeholder="请输入项目编号"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              label="项目类型"
              prop="type"
            >
              <el-select
                v-model="form.type"
                placeholder="请选择"
                style="width: 100%"
              >
                <el-option
                  v-for="t in typeOptions"
                  :key="t"
                  :label="t"
                  :value="t"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              label="优先级"
              prop="priority"
            >
              <el-select
                v-model="form.priority"
                placeholder="请选择"
                style="width: 100%"
              >
                <el-option
                  v-for="p in priorityOptions"
                  :key="p"
                  :label="p"
                  :value="p"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item
              label="项目状态"
              prop="status"
            >
              <el-select
                v-model="form.status"
                placeholder="请选择"
                style="width: 100%"
              >
                <el-option
                  v-for="s in statusOptions"
                  :key="s"
                  :label="s"
                  :value="s"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="startDate">
              <template #label>
                <el-tooltip
                  content="开始日期不能晚于结束日期"
                  placement="top"
                >
                  <span>开始日期</span>
                </el-tooltip>
              </template>
              <el-date-picker
                v-model="form.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item prop="endDate">
              <template #label>
                <el-tooltip
                  content="开始日期不能晚于结束日期"
                  placement="top"
                >
                  <span>结束日期</span>
                </el-tooltip>
              </template>
              <el-date-picker
                v-model="form.endDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择结束日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="合同金额(元)"
              prop="contractAmount"
            >
              <el-input-number
                v-model="form.contractAmount"
                :min="0"
                :precision="2"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="预算金额(元)"
              prop="budget"
            >
              <el-input-number
                v-model="form.budget"
                :min="0"
                :precision="2"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item
              label="项目地区"
              prop="region"
            >
              <el-cascader
                v-model="regionArray"
                :options="regionData"
                :props="{ expandTrigger: 'hover' }"
                placeholder="请选择省/市/区"
                style="width: 100%"
                popper-class="region-cascader-popper"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="项目地址">
              <el-input
                v-model="form.address"
                placeholder="请输入项目详细地址"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="项目描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="请输入项目描述"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>

      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            客户资料
          </div>
        </template>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              label="客户名称"
              prop="customerId"
            >
              <el-select
                v-model="form.customerId"
                placeholder="请选择客户"
                filterable
                clearable
                style="width: 100%"
                @change="handleCustomerChange"
              >
                <el-option
                  v-for="c in customerOptions"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="联系人"
              prop="customerContact"
            >
              <el-input
                v-model="form.customerContact"
                placeholder="请输入联系人"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="联系电话"
              prop="customerPhone"
            >
              <el-input
                v-model="form.customerPhone"
                placeholder="请输入联系电话"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系邮箱">
              <el-input
                v-model="form.customerEmail"
                placeholder="请输入联系邮箱"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>

      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            项目团队
          </div>
        </template>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              label="项目经理"
              prop="managerId"
            >
              <el-select
                v-model="form.managerId"
                placeholder="请选择项目经理"
                filterable
                clearable
                style="width: 100%"
                @change="handleManagerChange"
              >
                <el-option
                  v-for="u in managerOptions"
                  :key="u.id"
                  :label="u.name || u.username"
                  :value="u.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经理电话">
              <el-input
                v-model="form.managerPhone"
                placeholder="选择项目经理后自动填充"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>

      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>设计图纸</span>
            <BaseButton
              variant="primary"
              size="small"
              icon-name="Plus"
              style="float: right"
              @click="addDrawing"
            >
              添加图纸
            </BaseButton>
          </div>
        </template>
        <el-table
          :data="form.designDrawings"
          stripe
        >
          <el-table-column
            label="图纸名称"
            min-width="200"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.name"
                placeholder="请输入图纸名称"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="类型"
            width="120"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.type"
                placeholder="图纸类型"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="文件大小"
            width="120"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.size"
                placeholder="如:2.4MB"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="上传日期"
            width="160"
          >
            <template #default="{ row }">
              <el-date-picker
                v-model="row.uploadDate"
                type="date"
                value-format="YYYY-MM-DD"
                size="small"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="80"
          >
            <template #default="{ $index }">
              <BaseButton
                variant="link-danger"
                size="small"
                @click="removeDrawing($index)"
              >
                删除
              </BaseButton>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="form.designDrawings.length === 0"
          description="暂无图纸，请点击右上角添加"
        />
      </el-card>
    </el-form>
  </div>
</template>

<script setup>
/**
 * 项目管理 - 新建/编辑页
 * 业务用途：录入或修改项目信息；根据路由是否带 id 自动切换「新建」与「编辑」模式。
 * 主要功能区：基本信息、客户资料、项目团队、设计图纸（可动态增删）四个表单卡片，含完整表单校验。
 * 数据来源：projectStore（获取详情 fetchDetail、新增 addProject、更新 updateProject），
 *          下拉选项来自 @/constants/options；提交使用 useSubmitGuard 防重复提交。
 */
import { ref, reactive, onMounted, computed } from 'vue'
import dayjs from 'dayjs'
import { useRouter, useRoute } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { projectStatusOptions, projectPriorityOptions, projectTypeOptions } from '@/constants/options'
import { feedback } from '@/utils/feedback'
import { useSubmitGuard } from '@/utils/submitGuard'
import { customerApi } from '@/api/customer'
import { systemApi } from '@/api/system'
import { regionData } from 'element-china-area-data'

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()
const formRef = ref()

const statusOptions = projectStatusOptions
const priorityOptions = projectPriorityOptions
const typeOptions = projectTypeOptions

const isEdit = computed(() => !!route.params.id)

// 客户和项目经理下拉选项
const customerOptions = ref([])
const managerOptions = ref([])

// 地区级联选择器选中值（数组）：与 form.region（字符串 "省/市/区"）双向同步
// el-cascader 的 v-model 绑定数组，提交时 join('/') 存到 form.region，回显时 split('/') 转回数组
const regionArray = ref([])

const form = reactive({
  name: '',
  code: '',
  type: '新建',
  priority: '中',
  status: '进行中',
  startDate: '',
  endDate: '',
  contractAmount: 0,
  budget: 0,
  region: '',
  address: '',
  description: '',
  customerId: null,
  customerName: '',
  customerContact: '',
  customerPhone: '',
  customerEmail: '',
  managerId: null,
  manager: '',
  managerPhone: '',
  designDrawings: []
})

// 表单校验规则：含必填项、手机号/邮箱格式校验，以及结束日期须晚于开始日期的自定义校验
const rules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入项目编号', trigger: 'blur' }],
  type: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  status: [{ required: true, message: '请选择项目状态', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (value && form.startDate && new Date(value) <= new Date(form.startDate)) {
          callback(new Error('结束日期必须晚于开始日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  contractAmount: [{ required: true, message: '请输入合同金额', trigger: 'blur' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  customerContact: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  customerPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ],
  customerEmail: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  managerId: [{ required: true, message: '请选择项目经理', trigger: 'change' }],
  managerPhone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ]
}

/** 选择客户时自动填充联系人/电话/邮箱 */
function handleCustomerChange(customerId) {
  const customer = customerOptions.value.find(c => c.id === customerId)
  if (customer) {
    form.customerName = customer.name || ''
    form.customerContact = customer.contactPerson || ''
    form.customerPhone = customer.phone || ''
    form.customerEmail = customer.email || ''
  } else {
    form.customerName = ''
    form.customerContact = ''
    form.customerPhone = ''
    form.customerEmail = ''
  }
}

/** 选择项目经理时自动填充经理姓名/电话 */
function handleManagerChange(managerId) {
  const user = managerOptions.value.find(u => u.id === managerId)
  if (user) {
    form.manager = user.name || user.username || ''
    form.managerPhone = user.phone || ''
  } else {
    form.manager = ''
    form.managerPhone = ''
  }
}

// 编辑模式下：进入页面拉取项目详情，并把详情字段回填到表单；同时加载客户和项目经理下拉
onMounted(async () => {
  // 修复：原 Promise.all 在 systemApi.queryUsers 无权限(403)时整体 reject，
  // 导致 customerApi.listAll 虽成功但客户数据未赋值，客户下拉为空。
  // 改用 Promise.allSettled 使每个请求独立失败，互不影响。
  const [custResult, userResult] = await Promise.allSettled([
    customerApi.listAll(),
    systemApi.queryUsers({ pageNum: 1, pageSize: 200 })
  ])
  if (custResult.status === 'fulfilled') {
    customerOptions.value = custResult.value.data || []
  } else {
    console.error('加载客户下拉失败:', custResult.reason)
  }
  if (userResult.status === 'fulfilled') {
    // 筛选启用的用户作为项目经理候选
    managerOptions.value = (userResult.value.data?.records || []).filter(u => u.status === 1)
  } else {
    console.error('加载项目经理下拉失败:', userResult.reason)
  }

  if (route.params.id) {
    try {
      await projectStore.fetchDetail(route.params.id)
      const project = projectStore.currentProject
      if (project) {
        // 深拷贝逐字段回填，避免直接引用 store 数据
        Object.keys(form).forEach(key => {
          if (project[key] !== undefined) form[key] = JSON.parse(JSON.stringify(project[key]))
        })
        // 地区级联回显：region 字符串 "省/市/区" 转为数组供 el-cascader 使用
        regionArray.value = form.region ? form.region.split('/') : []
      }
    } catch (error) {
      console.error('加载项目详情失败:', error)
    }
  }
})

/** 新增一条设计图纸空记录到表格（id 为 null 表示新增，后端 diff 同步据此识别） */
function addDrawing() {
  form.designDrawings.push({
    id: null,
    name: '',
    type: '图纸',
    // P3 修复：原 new Date().toISOString() 取 UTC 日期，东八区凌晨 0-8 点会早一天；
    // 改用 dayjs 本地日期，与项目其他位置统一
    uploadDate: dayjs().format('YYYY-MM-DD'),
    size: ''
  })
}

/** 删除指定索引的设计图纸 @param {number} index 行索引 */
function removeDrawing(index) {
  form.designDrawings.splice(index, 1)
}

// 保存处理（useSubmitGuard 返回带防重复提交的方法与 submitting 加载态）
// 校验通过后：编辑模式调用更新接口，新建模式调用新增接口，成功后返回列表页
const [handleSave, submitting] = useSubmitGuard(async () => {
  // 修复 H9: Element Plus 的 validate(callback) 不会 await callback 内的 Promise，
  // 导致 useSubmitGuard 的 submitting 状态在 validate 解析后立即释放，防重复提交失效。
  // 改为 try-await 模式：先 await validate() 校验，通过后再执行保存逻辑。
  try {
    await formRef.value.validate()
  } catch {
    return // 校验失败，直接返回，不执行后续保存逻辑
  }
  // 地区级联提交：数组 ["省","市","区"] 转为字符串 "省/市/区" 存入 form.region
  form.region = regionArray.value.join('/')
  // 校验通过，执行保存逻辑
  if (isEdit.value) {
    await projectStore.updateProject(route.params.id, form)
  } else {
    await projectStore.addProject(form)
  }
  feedback.success('保存成功')
  router.push('/projects')
})
</script>

<style scoped>
.project-form {
  max-width: 1200px;
}

.form-card {
  margin-bottom: 20px;
}

.card-header {
  font-weight: 600;
  font-size: 15px;
}
</style>
