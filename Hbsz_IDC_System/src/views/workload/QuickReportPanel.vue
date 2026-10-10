<template>
  <div class="quick-report">
    <!-- ===== 顶部操作条：日期 + 项目（多项目时）+ 复制昨天 + 添加项 + 提交 ===== -->
    <div class="qr-toolbar">
      <div class="qr-toolbar-left">
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="填报日期"
          :clearable="false"
          :disabled-date="(d) => d.getTime() > Date.now()"
          style="width: 150px"
          @change="loadContext"
        />
        <el-select
          v-if="myProjects.length > 1"
          v-model="projectId"
          style="width: 240px"
          @change="loadContext"
        >
          <el-option
            v-for="p in myProjects"
            :key="p.projectId"
            :label="p.projectName + (p.roleInTeam === 'leader' ? '（我是组长）' : '')"
            :value="p.projectId"
          />
        </el-select>
        <span
          v-else-if="myProjects.length === 1"
          class="qr-project-name"
        >
          {{ myProjects[0].projectName }}
        </span>
      </div>
      <div class="qr-toolbar-right">
        <el-button
          :icon="DocumentCopy"
          :disabled="!hasYesterday || loading"
          @click="copyYesterday"
        >
          复制昨天
        </el-button>
        <el-button
          :icon="Search"
          :disabled="!projectId || totalItemCount === 0"
          @click="openItemPicker"
        >
          添加清单项
        </el-button>
        <el-button
          v-if="canReport"
          type="primary"
          :icon="Check"
          :loading="saving"
          :disabled="!projectId || editedCount === 0"
          @click="submit"
        >
          {{ submitButtonText }}
        </el-button>
      </div>
    </div>

    <!-- ===== 今日状态条 ===== -->
    <div
      v-if="statusSummary"
      class="qr-status-bar"
    >
      <el-icon class="qr-status-icon">
        <Calendar />
      </el-icon>
      <span class="qr-status-text">
        {{ dateLabel }} 已提交 <b class="num">{{ todayTotal }}</b> 项
        <template v-if="statusSummary.pending > 0">，<span class="qr-st-pending">待审核 <span class="num">{{ statusSummary.pending }}</span></span></template>
        <template v-if="statusSummary.approved > 0">，<span class="qr-st-approved">已通过 <span class="num">{{ statusSummary.approved }}</span></span></template>
        <template v-if="statusSummary.rejected > 0">
          ，<el-tooltip
            :content="firstRejectRemark"
            placement="top"
          >
            <span class="qr-st-rejected">已驳回 <span class="num">{{ statusSummary.rejected }}</span>（可修改后重新提交）</span>
          </el-tooltip>
          <el-button
            link
            type="primary"
            size="small"
            class="qr-resubmit-btn"
            :loading="saving"
            @click="resubmitRejected"
          >
            一键重报
          </el-button>
        </template>
      </span>
    </div>

    <!-- ===== 卡片流 ===== -->
    <div
      v-loading="loading"
      class="qr-cards-area"
    >
      <!-- 空态：未加入项目组 -->
      <el-empty
        v-if="!loading && myProjects.length === 0"
        description="您还未加入任何项目组，请联系项目经理将您添加为项目组成员"
        :image-size="90"
      />
      <!-- 空态：无常用项（首次使用） -->
      <el-empty
        v-else-if="!loading && cards.length === 0"
        description="还没有常用清单项：点击右上角「添加清单项」搜索并添加，提交一次后将成为您的常用项"
        :image-size="90"
      />
      <!-- 空态：常用项为空但今日已全部驳回？不会发生：todayRecords 会并入卡片 -->

      <div
        v-else
        class="qr-cards"
      >
        <div
          v-for="card in cards"
          :key="card.itemId"
          class="qr-card"
          :class="cardClass(card)"
        >
          <div class="qr-card-head">
            <span class="qr-card-category">{{ card.category }}</span>
            <span
              v-if="card.reviewStatus"
              class="qr-card-status"
              :class="'qr-status-' + card.reviewStatus"
            >{{ statusText(card.reviewStatus) }}</span>
          </div>
          <div
            class="qr-card-scope"
            :title="card.workScope"
          >
            {{ card.workScope }}
          </div>
          <div class="qr-card-meta">
            <span
              v-if="card.lastDate"
              class="qr-meta-item"
            >
              常填 <span class="num">{{ card.lastQuantity }}</span>{{ card.unit }}（近<span class="num">{{ card.fillCount }}</span>次）
            </span>
            <span
              v-if="yesterdayQty(card.itemId) !== null"
              class="qr-meta-item qr-meta-yesterday"
            >
              昨日 <span class="num">{{ yesterdayQty(card.itemId) }}</span>{{ card.unit }}
            </span>
            <span
              v-if="card.ratedWorkload"
              class="qr-meta-item qr-meta-rated"
              title="每人每日配额"
            >
              额定 <span class="num">{{ card.ratedWorkload }}</span>{{ card.unit }}
            </span>
          </div>
          <!-- 驳回原因 + 一键重报入口 -->
          <div
            v-if="card.reviewStatus === 'rejected'"
            class="qr-card-reject"
          >
            <span
              v-if="card.reviewRemark"
              class="qr-reject-text"
            >驳回原因：{{ card.reviewRemark }}</span>
            <span
              v-else
              class="qr-reject-text"
            >该填报已被驳回，可调整数量后重报</span>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="saving || Number(card.value) === 0"
              @click="resubmit(card)"
            >
              重报
            </el-button>
          </div>
          <!-- 步进器：approved 锁定只读 -->
          <div class="qr-stepper">
            <el-button
              :icon="Minus"
              circle
              size="small"
              :disabled="card.locked || card.value <= 0"
              @click="step(card, -1)"
            />
            <el-input-number
              v-model="card.value"
              :min="0"
              :precision="2"
              :controls="false"
              size="large"
              :disabled="card.locked"
              :class="['qr-input', { 'number-settle': card.settling }]"
              @change="onEdit(card)"
            />
            <span class="qr-unit">{{ card.unit }}</span>
            <el-button
              :icon="Plus"
              circle
              size="small"
              type="primary"
              :disabled="card.locked"
              @click="step(card, 1)"
            />
          </div>
        </div>
      </div>

      <!-- 底部提示：清单总数 -->
      <div
        v-if="cards.length > 0 && totalItemCount > cards.length"
        class="qr-more-tip"
      >
        已展示您常用的 {{ cards.length }} 项，项目清单共 {{ totalItemCount }} 项 ——
        需要「添加清单项」搜索更多
      </div>
    </div>

    <!-- ===== 添加清单项弹窗（搜索全部清单） ===== -->
    <el-dialog
      v-model="pickerVisible"
      title="添加清单项"
      width="560px"
      destroy-on-close
    >
      <el-input
        v-model="pickerKeyword"
        :prefix-icon="Search"
        placeholder="搜索作业类型 / 作业范围"
        clearable
        style="margin-bottom: 12px"
      />
      <el-table
        v-loading="pickerLoading"
        :data="filteredPickerItems"
        border
        stripe
        max-height="420"
        size="small"
        @row-click="addCard"
      >
        <el-table-column
          prop="category"
          label="作业类型"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="workScope"
          label="作业范围"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="unit"
          label="单位"
          width="60"
          align="center"
        />
        <el-table-column
          prop="ratedWorkload"
          label="额定"
          width="70"
          align="right"
        />
        <el-table-column
          label="操作"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="cardIds.has(row.id)"
              type="info"
              size="small"
            >
              已添加
            </el-tag>
            <el-button
              v-else
              link
              type="primary"
              size="small"
              @click.stop="addCard(row)"
            >
              添加
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            :description="pickerKeyword ? '未找到匹配的清单项' : '项目暂无清单项'"
            :image-size="70"
          />
        </template>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 快速填报面板（工作量统计重构核心组件）
 *
 * 目标：把"106 项矩阵找格子"重构为"我的常用项卡片流"，普通工人
 * 30 秒完成当日填报：进入即见常用卡片（近 14 天填报频次排序）→
 * 「复制昨天」一键带入昨日数量 → 微调 → 提交审核。
 *
 * 数据流：GET /workload/my/report-context（常用项 + 今日回显 + 昨日量）
 * 一次加载；保存复用 POST /workload/records（审核链路/成员校验/upsert
 * 后端单点不变），workerUserId 固定为当前登录用户（myUserId 回传）。
 *
 * 卡片状态：approved 锁定只读（已计入统计）；pending 可改；
 * rejected 可改后重新提交（附驳回原因）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { workloadApi } from '@/api/workload'
import { feedback } from '@/utils/feedback'
import { Check, Calendar, DocumentCopy, Minus, Plus, Search } from '@element-plus/icons-vue'

const emit = defineEmits(['submitted'])

// ===== 上下文状态 =====
const loading = ref(false)
const saving = ref(false)
const date = ref(new Date().toISOString().slice(0, 10))
const projectId = ref(null)
const myUserId = ref(null)
const myProjects = ref([])
const totalItemCount = ref(0)
const todayRecords = ref({})     // itemId → {quantity, reviewStatus, reviewRemark}
const yesterdayRecords = ref({}) // itemId → quantity

// ===== 卡片（常用项 ∪ 今日已填 ∪ 会话内手动添加） =====
const cards = ref([])

// ===== 添加项弹窗 =====
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerKeyword = ref('')
const allItems = ref([])

const canReport = computed(() => myUserId.value !== null && myProjects.value.length > 0)

/** 会话内已添加的卡片 itemId 集合（弹窗"已添加"标记用） */
const cardIds = computed(() => new Set(cards.value.map(c => c.itemId)))

/** 昨日是否有可复制数据 */
const hasYesterday = computed(() => Object.keys(yesterdayRecords.value).length > 0)

/** 已编辑（值>0 且非锁定）的卡片数 */
const editedCount = computed(() =>
  cards.value.filter(c => !c.locked && Number(c.value) > 0).length
)

const submitButtonText = computed(() =>
  editedCount.value > 0 ? `提交审核（${editedCount.value} 项）` : '提交审核'
)

/** 今日状态汇总 */
const statusSummary = computed(() => {
  const values = Object.values(todayRecords.value)
  if (values.length === 0) return null
  const sum = { pending: 0, approved: 0, rejected: 0 }
  values.forEach(v => { if (sum[v.reviewStatus] !== undefined) sum[v.reviewStatus]++ })
  return sum
})

const todayTotal = computed(() => Object.keys(todayRecords.value).length)

const firstRejectRemark = computed(() => {
  const rejected = cards.value.find(c => c.reviewStatus === 'rejected' && c.reviewRemark)
  return rejected ? rejected.reviewRemark : ''
})

const dateLabel = computed(() => (date.value === new Date().toISOString().slice(0, 10) ? '今天' : date.value))

const filteredPickerItems = computed(() => {
  const kw = pickerKeyword.value.trim().toLowerCase()
  if (!kw) return allItems.value
  return allItems.value.filter(
    it => (it.category || '').toLowerCase().includes(kw) || (it.workScope || '').toLowerCase().includes(kw)
  )
})

function yesterdayQty(itemId) {
  const qty = yesterdayRecords.value[itemId]
  return (qty !== undefined && qty !== null) ? qty : null
}

function statusText(status) {
  return { pending: '待审核', approved: '已通过', rejected: '已驳回' }[status] || status
}

function cardClass(card) {
  return {
    'qr-card--approved': card.reviewStatus === 'approved',
    'qr-card--rejected': card.reviewStatus === 'rejected'
  }
}

// ====================================================================
// 数据加载
// ====================================================================

async function loadContext() {
  loading.value = true
  try {
    // 首次不传 projectId：拿项目列表；单项目自动选中后带 projectId 二次请求
    const params = { date: date.value }
    if (projectId.value) params.projectId = projectId.value
    const res = await workloadApi.getMyReportContext(params)
    const data = res.data || {}
    myUserId.value = data.myUserId
    myProjects.value = data.projects || []
    if (!projectId.value && myProjects.value.length > 0) {
      projectId.value = myProjects.value[0].projectId
      return loadContext()
    }
    todayRecords.value = data.todayRecords || {}
    yesterdayRecords.value = data.yesterdayRecords || {}
    totalItemCount.value = data.totalItemCount || 0
    buildCards(data.frequentItems || [])
  } catch (e) {
    feedback.error(e?.message || '快速填报数据加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 组装卡片列表 = 常用项（频次序）∪ 今日已填项（含已驳回提醒）∪ 会话内已添加项。
 * 值回显今日已有数量；approved 卡片锁定。
 */
function buildCards(frequentItems) {
  const byId = new Map()
  for (const fi of frequentItems) {
    byId.set(fi.itemId, makeCard(fi))
  }
  // 今日已填但不在常用列表的项（如今天早上手工添加过）也要回显
  for (const itemId of Object.keys(todayRecords.value)) {
    const id = Number(itemId)
    if (!byId.has(id)) {
      byId.set(id, makeCard({ itemId: id, category: '今日填报', workScope: '（清单项 #' + id + '）', unit: '', ratedWorkload: null, fillCount: 0, lastQuantity: null, lastDate: null }))
      // 尝试用全量清单补全详情（弹窗打开时 allItems 已有则直接用）
      const detail = allItems.value.find(it => it.id === id)
      if (detail) applyItemDetail(byId.get(id), detail)
    }
  }
  cards.value = [...byId.values()]
}

function makeCard(fi) {
  const todayCell = todayRecords.value[fi.itemId]
  const card = reactive({
    itemId: fi.itemId,
    category: fi.category,
    workScope: fi.workScope,
    unit: fi.unit || '',
    ratedWorkload: fi.ratedWorkload,
    fillCount: fi.fillCount || 0,
    lastQuantity: fi.lastQuantity,
    lastDate: fi.lastDate,
    // 今日回显：已填数量为初始值，否则 0（复制昨天后填充）
    value: todayCell ? Number(todayCell.quantity) : 0,
    reviewStatus: todayCell ? todayCell.reviewStatus : null,
    reviewRemark: todayCell ? todayCell.reviewRemark : null
  })
  card.locked = card.reviewStatus === 'approved'
  return card
}

function applyItemDetail(card, item) {
  card.category = item.category
  card.workScope = item.workScope
  card.unit = item.unit || ''
  card.ratedWorkload = item.ratedWorkload
}

// ====================================================================
// 交互
// ====================================================================

/** 步进：+1 档 = 昨日量 → 常填量 → 1；-1 档同幅递减（不小于 0） */
function step(card, direction) {
  const unit = Number(yesterdayQty(card.itemId) ?? card.lastQuantity ?? 1) || 1
  const next = Number(card.value || 0) + direction * unit
  card.value = Math.max(0, Number(next.toFixed(2)))
  onEdit(card)
}

// ===== 数字落定微动效（Glow+Grain Token 铺开）=====
// 值变化时在输入区重放 global.css 的 numberSettle 动画；
// timer 用 WeakMap 挂卡片外，避免污染 reactive 数据
const settleTimers = new WeakMap()

function triggerSettle(card) {
  clearTimeout(settleTimers.get(card))
  card.settling = false
  // 下一宏任务重挂 class：class 移除与添加隔一帧，连续变更时动画才能重放
  const t = setTimeout(() => {
    card.settling = true
    setTimeout(() => { card.settling = false }, 300)
  }, 20)
  settleTimers.set(card, t)
}

function onEdit(card) {
  if (card.reviewStatus === 'pending' || card.reviewStatus === 'rejected') {
    // 编辑中的 pending 项保持原状态展示（提交后统一回 pending）
  }
  triggerSettle(card)
}

/** 复制昨天：对空值且非锁定卡片带入昨日数量（已有值不覆盖，可先清零） */
function copyYesterday() {
  let copied = 0
  for (const card of cards.value) {
    if (card.locked) continue
    const y = yesterdayQty(card.itemId)
    if (y !== null && Number(card.value) === 0) {
      card.value = Number(y)
      triggerSettle(card)
      copied++
    }
  }
  // 昨日填过但不在当前卡片列表的项：自动补成卡片
  for (const [itemId, qty] of Object.entries(yesterdayRecords.value)) {
    const id = Number(itemId)
    if (!cardIds.value.has(id)) {
      const detail = allItems.value.find(it => it.id === id)
      const card = makeCard({
        itemId: id,
        category: detail?.category || '昨日项目',
        workScope: detail?.workScope || '（清单项 #' + id + '）',
        unit: detail?.unit || '',
        ratedWorkload: detail?.ratedWorkload ?? null,
        fillCount: 0, lastQuantity: null, lastDate: null
      })
      card.value = Number(qty)
      cards.value.push(card)
      copied++
    }
  }
  if (copied === 0) {
    feedback.warning('没有可复制的昨日数量（卡片已有值或已审核锁定）')
  } else {
    feedback.success(`已复制 ${copied} 项昨日数量，可直接微调后提交`)
  }
}

// ===== 添加清单项 =====

async function openItemPicker() {
  pickerVisible.value = true
  if (allItems.value.length === 0) {
    pickerLoading.value = true
    try {
      const res = await workloadApi.listItems({ projectId: projectId.value, statisticsDate: date.value })
      allItems.value = res.data || []
      // 顺带补全今日已填但缺详情的卡片
      for (const card of cards.value) {
        if (card.category === '今日填报') {
          const detail = allItems.value.find(it => it.id === card.itemId)
          if (detail) applyItemDetail(card, detail)
        }
      }
    } catch (e) {
      feedback.error(e?.message || '清单加载失败')
    } finally {
      pickerLoading.value = false
    }
  }
}

function addCard(row) {
  if (cardIds.value.has(row.id)) return
  const card = makeCard({
    itemId: row.id,
    category: row.category,
    workScope: row.workScope,
    unit: row.unit,
    ratedWorkload: row.ratedWorkload,
    fillCount: 0, lastQuantity: null, lastDate: null
  })
  cards.value.push(card)
  feedback.success(`已添加「${row.workScope}」，填入数量后将随本次提交`)
}

// ===== 提交 =====

async function submit() {
  const entries = cards.value
    .filter(c => !c.locked && Number(c.value) > 0)
    .map(c => ({ itemId: c.itemId, workerUserId: myUserId.value, quantity: Number(c.value) }))
  if (entries.length === 0) {
    feedback.warning('请先填写至少一项数量')
    return
  }
  saving.value = true
  try {
    await workloadApi.saveRecords({
      projectId: projectId.value,
      recordDate: date.value,
      records: entries
    })
    feedback.success(`已提交 ${entries.length} 项，等待项目经理审核`)
    emit('submitted')
    await loadContext()
  } catch (e) {
    feedback.error(e?.message || '提交失败')
  } finally {
    saving.value = false
  }
}

// ===== 一键重报（被驳回项） =====
// 后端 upsert 语义：rejected 记录重新提交会覆盖数量并重置回待审核，
// 因此直接以当前卡片值提交即可完成重报，无需删除原记录。

/** 全部驳回项一键重报（状态条入口） */
async function resubmitRejected() {
  await doResubmit(cards.value.filter(c => c.reviewStatus === 'rejected' && !c.locked))
}

/** 单张驳回卡片重报（卡片入口，自动带入当前数量） */
async function resubmit(card) {
  await doResubmit([card])
}

async function doResubmit(targets) {
  const entries = targets
    .filter(c => Number(c.value) > 0)
    .map(c => ({ itemId: c.itemId, workerUserId: myUserId.value, quantity: Number(c.value) }))
  const zeroCount = targets.length - entries.length
  if (entries.length === 0) {
    feedback.warning('被驳回项当前数量为 0，请先填写数量后再重报')
    return
  }
  saving.value = true
  try {
    await workloadApi.saveRecords({
      projectId: projectId.value,
      recordDate: date.value,
      records: entries
    })
    feedback.success(
      `已重新提交 ${entries.length} 项，等待项目经理审核` +
      (zeroCount > 0 ? `（另有 ${zeroCount} 项数量为 0 未提交）` : '')
    )
    emit('submitted')
    await loadContext()
  } catch (e) {
    feedback.error(e?.message || '重报失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadContext)

defineExpose({ reload: loadContext })
</script>

<style scoped>
/* ===== 布局 ===== */
.quick-report { display: flex; flex-direction: column; gap: 12px; }

.qr-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.qr-toolbar-left { display: flex; align-items: center; gap: 10px; }
.qr-toolbar-right { display: flex; align-items: center; gap: 8px; }
.qr-project-name { font-weight: 600; color: var(--text-primary, #1f2937); }

/* ===== 状态条 ===== */
.qr-status-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-radius: 8px;
  background: #e6f7ff; color: #0958a8; font-size: 13px;
}
.qr-status-icon { font-size: 15px; }
.qr-status-text b { font-size: 15px; }
.qr-st-pending { color: #d48806; font-weight: 600; }
.qr-st-approved { color: #389e0d; font-weight: 600; }
.qr-st-rejected { color: #d4380d; font-weight: 600; cursor: help; }

/* ===== 卡片流 ===== */
.qr-cards-area { min-height: 200px; }
.qr-cards {
  display: grid; gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
}
.qr-card {
  display: flex; flex-direction: column; gap: 8px;
  padding: 14px; border: 1px solid #e5e7eb; border-radius: 8px;
  background: #fff; transition: box-shadow 0.2s, border-color 0.2s;
}
.qr-card:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.qr-card--approved { border-color: #b7eb8f; background: #f6ffed; }
.qr-card--rejected { border-color: #ffa39e; background: #fff1f0; }

.qr-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.qr-card-category {
  font-size: 12px; padding: 1px 8px; border-radius: 4px;
  background: #e6f7ff; color: #1890ff; max-width: 70%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.qr-card-status { font-size: 12px; font-weight: 600; }
.qr-status-pending { color: #d48806; }
.qr-status-approved { color: #389e0d; }
.qr-status-rejected { color: #d4380d; }

.qr-card-scope {
  font-size: 14px; font-weight: 600; color: var(--text-primary, #1f2937);
  min-height: 20px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.qr-card-meta { display: flex; flex-wrap: wrap; gap: 10px; font-size: 12px; color: #8a919f; }
.qr-meta-yesterday { color: #d48806; }
.qr-card-reject {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  font-size: 12px; color: #d4380d; background: rgba(245, 34, 45, 0.06);
  border-radius: 4px; padding: 4px 8px;
}
.qr-reject-text { flex: 1; min-width: 0; }
.qr-resubmit-btn { margin-left: 8px; }

/* ===== 步进器 ===== */
.qr-stepper { display: flex; align-items: center; gap: 8px; margin-top: 2px; }
.qr-input { flex: 1; }
.qr-input :deep(.el-input__inner) {
  text-align: center; font-size: 18px; font-weight: 700;
  /* 等宽数字栈（--font-numeric Token）：多卡片数值列稳定对齐 */
  font-family: var(--font-numeric);
  font-variant-numeric: tabular-nums; color: #1890ff;
}
.qr-unit { min-width: 24px; font-size: 13px; color: #8a919f; }
.qr-card--approved .qr-input :deep(.el-input__inner) { color: #389e0d; }

.qr-more-tip { margin-top: 12px; font-size: 12px; color: #8a919f; text-align: center; }
</style>
