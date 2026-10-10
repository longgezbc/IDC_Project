<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <div class="page-title">
          系统设置
        </div>
        <div class="page-subtitle">
          管理个人信息、系统配置和通知偏好
        </div>
      </div>
    </div>

    <el-row :gutter="20">
      <!-- 左侧菜单 -->
      <el-col
        :xs="24"
        :sm="6"
        :md="5"
        :lg="4"
      >
        <div
          class="card-box"
          style="padding: 0"
        >
          <el-menu
            :default-active="activeMenu"
            @select="activeMenu = $event"
          >
            <el-menu-item index="profile">
              <el-icon><User /></el-icon>
              <span>个人资料</span>
            </el-menu-item>
            <el-menu-item index="security">
              <el-icon><Lock /></el-icon>
              <span>账号安全</span>
            </el-menu-item>
            <el-menu-item index="notify">
              <el-icon><Bell /></el-icon>
              <span>通知设置</span>
            </el-menu-item>
            <el-menu-item
              v-if="canManageSystemConfig"
              index="system"
            >
              <el-icon><Setting /></el-icon>
              <span>系统配置</span>
            </el-menu-item>
            <el-menu-item index="about">
              <el-icon><InfoFilled /></el-icon>
              <span>关于系统</span>
            </el-menu-item>
          </el-menu>
        </div>
      </el-col>

      <!-- 右侧内容 -->
      <el-col
        :xs="24"
        :sm="18"
        :md="19"
        :lg="20"
      >
        <!-- 个人资料 -->
        <div
          v-if="activeMenu === 'profile'"
          class="card-box"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--primary"><el-icon><User /></el-icon></span>
              <span>个人资料</span>
            </div>
          </div>

          <div class="profile-header">
            <el-avatar
              :size="80"
              :src="profileForm.avatar || defaultAvatar"
            />
            <div class="profile-info">
              <h3>{{ profileForm.name || profileForm.username }}</h3>
              <p>{{ roleText }}</p>
              <el-tag
                size="small"
                type="success"
              >
                在线
              </el-tag>
            </div>
            <BaseButton
              variant="plain"
              icon-name="Upload"
            >
              更换头像
            </BaseButton>
          </div>

          <el-divider />

          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="100px"
            style="max-width: 600px"
          >
            <el-form-item label="用户名">
              <el-input
                v-model="profileForm.username"
                disabled
              />
            </el-form-item>
            <el-form-item
              label="姓名"
              prop="name"
            >
              <el-input v-model="profileForm.name" />
            </el-form-item>
            <el-form-item
              label="手机号"
              prop="phone"
            >
              <el-input v-model="profileForm.phone" />
            </el-form-item>
            <el-form-item
              label="邮箱"
              prop="email"
            >
              <el-input v-model="profileForm.email" />
            </el-form-item>
            <el-form-item label="部门">
              <el-input v-model="profileForm.department" />
            </el-form-item>
            <el-form-item label="职位">
              <el-input v-model="profileForm.position" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input
                v-model="profileForm.bio"
                type="textarea"
                :rows="3"
              />
            </el-form-item>
            <el-form-item>
              <BaseButton
                variant="primary"
                :loading="savingProfile"
                @click="handleSaveProfile"
              >
                保存修改
              </BaseButton>
              <BaseButton @click="handleResetProfile">
                重置
              </BaseButton>
            </el-form-item>
          </el-form>
        </div>

        <!-- 账号安全 -->
        <div
          v-else-if="activeMenu === 'security'"
          class="card-box"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--danger"><el-icon><Lock /></el-icon></span>
              <span>账号安全</span>
            </div>
          </div>

          <div class="security-list">
            <div class="security-item">
              <div class="security-info">
                <div class="security-name">
                  <el-icon><Key /></el-icon>
                  登录密码
                </div>
                <div class="security-desc">
                  建议定期更换密码，确保账号安全
                </div>
                <el-tag
                  size="small"
                  type="success"
                >
                  已设置
                </el-tag>
              </div>
              <BaseButton
                variant="link"
                @click="showPasswordDialog = true"
              >
                修改
              </BaseButton>
            </div>

            <el-divider />

            <div class="security-item">
              <div class="security-info">
                <div class="security-name">
                  <el-icon><Iphone /></el-icon>
                  绑定手机
                </div>
                <div class="security-desc">
                  已绑定手机：{{ maskedPhone }}
                </div>
                <el-tag
                  size="small"
                  type="success"
                >
                  已绑定
                </el-tag>
              </div>
              <BaseButton variant="link">
                更换
              </BaseButton>
            </div>

            <el-divider />

            <div class="security-item">
              <div class="security-info">
                <div class="security-name">
                  <el-icon><Message /></el-icon>
                  绑定邮箱
                </div>
                <div class="security-desc">
                  已绑定邮箱：{{ maskedEmail }}
                </div>
                <el-tag
                  size="small"
                  type="success"
                >
                  已绑定
                </el-tag>
              </div>
              <BaseButton variant="link">
                更换
              </BaseButton>
            </div>

            <el-divider />

            <div class="security-item">
              <div class="security-info">
                <div class="security-name">
                  <el-icon><Unlock /></el-icon>
                  双因素认证
                </div>
                <div class="security-desc">
                  开启后登录需输入验证码，提升账号安全性
                </div>
                <el-tag
                  size="small"
                  type="warning"
                >
                  未开启
                </el-tag>
              </div>
              <el-switch v-model="securityForm.twoFactor" />
            </div>
          </div>
        </div>

        <!-- 通知设置 -->
        <div
          v-else-if="activeMenu === 'notify'"
          class="card-box"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--warning"><el-icon><Bell /></el-icon></span>
              <span>通知设置</span>
            </div>
          </div>

          <el-form
            label-width="160px"
            style="max-width: 700px"
          >
            <el-form-item label="站内通知">
              <el-switch v-model="notifyConfig.inApp" />
              <span class="notify-desc">关键节点提醒、任务分配等</span>
            </el-form-item>
            <el-form-item label="邮件通知">
              <el-switch v-model="notifyConfig.email" />
              <span class="notify-desc">重要事件通过邮件发送</span>
            </el-form-item>
            <el-form-item label="短信通知">
              <el-switch v-model="notifyConfig.sms" />
              <span class="notify-desc">紧急情况通过短信通知</span>
            </el-form-item>
            <el-divider>通知内容</el-divider>
            <el-form-item label="项目进度提醒">
              <el-switch v-model="notifyConfig.progress" />
            </el-form-item>
            <el-form-item label="任务到期提醒">
              <el-switch v-model="notifyConfig.taskDue" />
            </el-form-item>
            <el-form-item label="文档更新提醒">
              <el-switch v-model="notifyConfig.docUpdate" />
            </el-form-item>
            <el-form-item label="项目归档提醒">
              <el-switch v-model="notifyConfig.archive" />
            </el-form-item>
            <el-form-item>
              <BaseButton
                variant="primary"
                :loading="savingNotify"
                @click="handleSaveNotify"
              >
                保存设置
              </BaseButton>
            </el-form-item>
          </el-form>
        </div>

        <!-- 系统配置 -->
        <div
          v-else-if="activeMenu === 'system'"
          class="card-box"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--purple"><el-icon><Setting /></el-icon></span>
              <span>系统配置</span>
            </div>
          </div>

          <el-form
            :model="systemConfig"
            label-width="140px"
            style="max-width: 600px"
          >
            <el-form-item label="系统名称">
              <el-input v-model="systemConfig.name" />
            </el-form-item>
            <el-form-item label="系统版本">
              <el-input
                v-model="systemConfig.version"
                disabled
              />
            </el-form-item>
            <el-form-item label="主题色">
              <el-color-picker v-model="systemConfig.themeColor" />
            </el-form-item>
            <el-form-item label="默认项目视图">
              <el-radio-group v-model="systemConfig.defaultView">
                <el-radio value="table">
                  表格视图
                </el-radio>
                <el-radio value="card">
                  卡片视图
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="每页显示条数">
              <el-select
                v-model="systemConfig.pageSize"
                style="width: 120px"
              >
                <el-option
                  :value="10"
                  label="10条"
                />
                <el-option
                  :value="20"
                  label="20条"
                />
                <el-option
                  :value="50"
                  label="50条"
                />
                <el-option
                  :value="100"
                  label="100条"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="自动保存">
              <el-switch v-model="systemConfig.autoSave" />
              <span class="notify-desc">表单数据自动保存</span>
            </el-form-item>
            <el-form-item>
              <BaseButton
                variant="primary"
                :loading="savingSystem"
                @click="handleSaveSystem"
              >
                保存配置
              </BaseButton>
              <BaseButton @click="handleResetSystem">
                恢复默认
              </BaseButton>
            </el-form-item>
          </el-form>
        </div>

        <!-- 关于系统 -->
        <div
          v-else-if="activeMenu === 'about'"
          class="card-box"
        >
          <div class="card-header">
            <div class="card-title">
              <span class="title-chip title-chip--primary"><el-icon><InfoFilled /></el-icon></span>
              <span>关于系统</span>
            </div>
          </div>

          <div class="about-content">
            <div class="about-logo">
              <img
                :src="logoImg"
                class="about-logo-img"
                alt="系统Logo"
              >
              <h2>IDC数据中心项目管理系统</h2>
              <p>IDC Data Center Project Management System</p>
            </div>

            <el-descriptions
              :column="2"
              border
              style="margin-top: 24px"
            >
              <el-descriptions-item label="系统版本">
                V1.0.0
              </el-descriptions-item>
              <el-descriptions-item label="发布日期">
                2026-06-22
              </el-descriptions-item>
              <el-descriptions-item label="开发框架">
                Vue 3 + Element Plus
              </el-descriptions-item>
              <el-descriptions-item label="构建工具">
                Vite
              </el-descriptions-item>
              <el-descriptions-item label="状态管理">
                Pinia
              </el-descriptions-item>
              <el-descriptions-item label="图表库">
                ECharts
              </el-descriptions-item>
              <el-descriptions-item label="授权类型">
                企业版
              </el-descriptions-item>
              <el-descriptions-item label="技术支持">
                support@example.com
              </el-descriptions-item>
            </el-descriptions>

            <el-divider />

            <div class="about-info">
              <h3>系统功能</h3>
              <ul>
                <li>项目信息管理：支持项目创建、编辑、归档，包含项目基本信息、客户资料等</li>
                <li>文档管理：支持各类技术文档、合同文件、验收报告的上传、分类、版本控制</li>
                <li>实施进度管理：提供甘特图可视化展示，支持任务分解、进度填报、关键节点提醒</li>
                <li>统计分析：项目数据统计分析，多维度报表展示</li>
                <li>系统设置：个性化配置，通知偏好设置</li>
              </ul>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="showPasswordDialog"
      title="修改密码"
      width="450px"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
      >
        <el-form-item
          label="原密码"
          prop="oldPassword"
        >
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item
          label="新密码"
          prop="newPassword"
        >
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
          />
          <div
            class="password-strength"
            :class="passwordStrength.level"
          >
            <div class="strength-bars">
              <span /><span /><span /><span />
            </div>
            <span class="strength-text">{{ passwordStrength.text }}</span>
          </div>
          <div class="password-tips">
            密码需 8-20 位，至少包含字母和数字
          </div>
        </el-form-item>
        <el-form-item
          label="确认密码"
          prop="confirmPassword"
        >
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <BaseButton @click="showPasswordDialog = false">
          取消
        </BaseButton>
        <BaseButton
          variant="primary"
          :loading="savingPassword"
          @click="handleChangePassword"
        >
          确认修改
        </BaseButton>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 系统设置页
 * 业务用途：集中管理个人资料、账号安全、通知偏好、系统配置及系统信息展示。
 * 主要功能区：左侧菜单切换（个人资料/账号安全/通知设置/系统配置/关于系统），
 *             右侧对应表单；含修改密码对话框。
 * 数据来源：userApi（个人资料、通知设置、修改密码）、userStore（刷新登录用户信息）、
 *          systemConfigStore（系统配置读写与主题色应用）。
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { feedback } from '@/utils/feedback'
import { useUserStore } from '@/stores/user'
import { useSystemConfigStore } from '@/stores/systemConfig'
import { userApi } from '@/api/user'
import { hasPermission } from '@/utils/permission'
// 关于页 Logo 图片
import logoImg from '@/static/logo/8da4edb7bc99f55c525d4722e1db8644.jpg'

const userStore = useUserStore()
const systemConfigStore = useSystemConfigStore()
const activeMenu = ref('profile')       // 当前激活的左侧菜单项
const showPasswordDialog = ref(false)   // 修改密码对话框显隐
const passwordFormRef = ref()           // 修改密码表单实例引用（用于校验/重置）
const profileFormRef = ref()            // 个人资料表单实例引用（用于校验/重置）

// 保存按钮 loading 状态（防重复提交）
const savingProfile = ref(false)
const savingNotify = ref(false)
const savingSystem = ref(false)
const savingPassword = ref(false)

// 系统配置菜单权限：需 system:config:edit 权限或 admin 角色才可见
// 路由注释明确"系统配置修改由后端 @RequiresPermission("system:config:edit") 控制"，
// 前端菜单可见性应与后端权限保持一致，避免无权限用户看到菜单后点击保存被 403 拒绝
const canManageSystemConfig = computed(() => hasPermission('system:config:edit'))

// 个人资料表单（用户名只读，其余可编辑）
const profileForm = reactive({
  username: '',
  name: '',
  avatar: '',
  phone: '',
  email: '',
  department: '',
  position: '',
  bio: ''
})

// 默认头像（Element Plus 内置）
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 角色显示文本（从 userStore 获取角色列表）
const roleText = computed(() => {
  const roles = userStore.roles || []
  if (roles.length === 0) return '普通用户'
  const roleMap = { admin: '系统管理员', project_manager: '项目经理', engineer: '工程师', user: '普通用户' }
  return roles.map(r => roleMap[r] || r).join(' / ')
})

// 手机号脱敏显示（保留前3后4，中间用****代替）
const maskedPhone = computed(() => {
  const phone = profileForm.phone
  if (!phone || phone.length < 7) return phone || '未绑定'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
})

// 邮箱脱敏显示（用户名部分保留首尾字符，中间用***代替）
const maskedEmail = computed(() => {
  const email = profileForm.email
  if (!email) return '未绑定'
  const atIndex = email.indexOf('@')
  if (atIndex <= 1) return email
  return email[0] + '***' + email.slice(atIndex)
})

// 账号安全设置（双因素认证开关）
const securityForm = reactive({
  twoFactor: false
})

// 通知偏好配置（通知渠道 + 通知内容开关）
const notifyConfig = reactive({
  inApp: true,
  email: true,
  sms: false,
  progress: true,
  taskDue: true,
  docUpdate: false,
  archive: true
})

// 系统配置（系统名称、主题色、默认视图、分页条数、自动保存等）
const systemConfig = reactive({
  name: 'IDC数据中心项目管理系统',
  version: 'V1.0.0',
  themeColor: '#1890ff',
  defaultView: 'table',
  pageSize: 20,
  autoSave: true
})

// 实时预览主题色变化
watch(() => systemConfig.themeColor, (newColor) => {
  systemConfigStore.applyThemeColor(newColor)
})

// 密码表单（原密码/新密码/确认密码）
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

/**
 * 计算密码强度
 * @param {string} pwd 待检测的密码
 * @returns {{level: string, text: string, score: number}} 强度等级/文案/评分
 * 关键逻辑：按长度、大小写、数字、特殊字符累加评分，长度不足 8 位直接判为弱。
 */
function checkPasswordStrength(pwd) {
  if (!pwd) return { level: '', text: '', score: 0 }
  let score = 0
  if (pwd.length >= 8) score++
  if (pwd.length >= 12) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  if (pwd.length < 8) score = 0

  if (score <= 1) return { level: 'weak', text: '弱', score }
  if (score <= 3) return { level: 'medium', text: '中', score }
  return { level: 'strong', text: '强', score }
}

const passwordStrength = computed(() => checkPasswordStrength(passwordForm.newPassword))  // 新密码强度（实时计算）

// 个人资料表单校验规则：姓名必填，手机号/邮箱格式校验
const profileRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}

// 修改密码表单校验规则：原密码必填，新密码需含字母+数字且不与原密码相同，确认密码需一致
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为 8-20 位', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(value)) {
          callback(new Error('密码必须包含字母和数字'))
        } else if (value === passwordForm.oldPassword) {
          callback(new Error('新密码不能与原密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/** 保存个人资料：表单校验通过后提交更新，并刷新登录用户信息 */
async function handleSaveProfile() {
  try {
    await profileFormRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  savingProfile.value = true
  try {
    await userApi.updateProfile(profileForm)
    await userStore.fetchProfile()
    feedback.success('个人资料保存成功')
  } catch (error) {
    console.error('保存个人资料失败:', error?.message)
    feedback.error('保存个人资料失败')
  } finally {
    savingProfile.value = false
  }
}

/** 重置个人资料：将表单恢复为最近一次加载的状态 */
function handleResetProfile() {
  profileFormRef.value?.resetFields()
}

/** 保存通知偏好设置 */
async function handleSaveNotify() {
  savingNotify.value = true
  try {
    await userApi.updateNotificationSettings(notifyConfig)
    feedback.success('通知设置保存成功')
  } catch (error) {
    console.error('保存通知设置失败:', error?.message)
    feedback.error('保存通知设置失败')
  } finally {
    savingNotify.value = false
  }
}

/** 保存系统配置：持久化到后端 store（含主题色、默认视图、分页条数等） */
async function handleSaveSystem() {
  savingSystem.value = true
  try {
    await systemConfigStore.saveConfig({
      name: systemConfig.name,
      themeColor: systemConfig.themeColor,
      defaultView: systemConfig.defaultView,
      pageSize: systemConfig.pageSize,
      autoSave: systemConfig.autoSave
    })
    feedback.success('系统配置已保存')
  } catch (error) {
    console.error('保存系统配置失败:', error?.message)
    feedback.error('保存系统配置失败')
  } finally {
    savingSystem.value = false
  }
}

/** 恢复系统配置默认值（仅本地重置并预览主题色，需再点保存才生效） */
function handleResetSystem() {
  systemConfig.name = 'IDC数据中心项目管理系统'
  systemConfig.themeColor = '#1890ff'
  systemConfig.defaultView = 'table'
  systemConfig.pageSize = 20
  systemConfig.autoSave = true
  systemConfigStore.applyThemeColor(systemConfig.themeColor)
  feedback.success('已恢复默认配置（未保存，请点击保存配置以生效）')
}

/** 修改密码：先做表单校验，通过后提交并清空表单、关闭对话框 */
async function handleChangePassword() {
  // 修复 M14: validate(callback) 不会 await callback 的 Promise，导致无法正确等待异步操作完成。改为 try-await 模式。
  try {
    await passwordFormRef.value.validate()
  } catch {
    return // 校验失败，直接返回
  }
  savingPassword.value = true
  try {
    await userApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    feedback.success('密码修改成功')
    showPasswordDialog.value = false
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordFormRef.value.resetFields()
  } catch (error) {
    console.error('密码修改失败:', error?.message)
    feedback.error('密码修改失败')
  } finally {
    savingPassword.value = false
  }
}

/** 加载个人资料和通知设置 */
async function loadSettings() {
  try {
    const [profileRes, notifyRes] = await Promise.all([
      userApi.getProfile(),
      userApi.getNotificationSettings()
    ])
    Object.assign(profileForm, profileRes.data)
    if (notifyRes.data) {
      Object.assign(notifyConfig, notifyRes.data)
    }
    // 系统配置从 store 加载（已在 App.vue 启动时加载）
    Object.assign(systemConfig, systemConfigStore.config)
  } catch (error) {
    console.error('加载设置失败:', error)
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
/* 菜单项选中状态 - 确保文字清晰可读 */
:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item.is-active) {
  color: #ffffff !important;
  background-color: #1890ff !important;
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #ffffff !important;
}

:deep(.el-menu-item:hover) {
  background-color: #e6f7ff !important;
  color: #1890ff !important;
}

:deep(.el-menu-item:hover .el-icon) {
  color: #1890ff !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-info {
  flex: 1;
}

.profile-info h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.profile-info p {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.security-list {
  max-width: 700px;
}

.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.security-info {
  flex: 1;
}

.security-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 6px;
}

.security-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.notify-desc {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.about-content {
  text-align: center;
}

.about-logo {
  padding: 20px 0;
  text-align: center;
}

/* 关于页 Logo 图片 */
.about-logo-img {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 12px;
  margin: 0 auto;
  display: block;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.about-logo h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
  margin: 16px 0 6px;
}

.about-logo p {
  font-size: 13px;
  color: #909399;
  letter-spacing: 1px;
}

.about-info {
  text-align: left;
  max-width: 700px;
  margin: 0 auto;
}

.about-info h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.about-info ul {
  padding-left: 20px;
  color: #6b7280;
  line-height: 1.8;
}

.about-info li {
  margin-bottom: 6px;
}

/* 密码强度指示器 */
.password-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
}

.strength-bars {
  display: flex;
  gap: 3px;
}

.strength-bars span {
  display: inline-block;
  width: 24px;
  height: 4px;
  background: #e4e7ed;
  border-radius: 2px;
  transition: background 0.2s;
}

.password-strength.weak .strength-bars span:nth-child(1) {
  background: #f5222d;
}

.password-strength.medium .strength-bars span:nth-child(-n+2) {
  background: #faad14;
}

.password-strength.strong .strength-bars span {
  background: #52c41a;
}

.password-strength.weak .strength-text {
  color: #f5222d;
}

.password-strength.medium .strength-text {
  color: #faad14;
}

.password-strength.strong .strength-text {
  color: #52c41a;
}

.password-tips {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}
</style>
