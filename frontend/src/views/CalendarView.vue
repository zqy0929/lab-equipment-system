<template>
  <div class="page-card">
    <div class="page-title">
      预约日历
      <span v-if="quota" class="quota-chip" :class="{ 'quota-chip--full': quota.atLimit }">
        同时预约 {{ quota.activeCount }} / {{ quota.maxActive }}
      </span>
      <el-tooltip :disabled="!quotaAtLimit && !cancelBanned" placement="bottom"
                  :content="cancelBanned ? '取消次数超限，暂时无法预约' : '已达同时预约数量上限，请等待已有预约审批结束或归还设备'">
        <span class="title-btn">
          <el-button type="primary" size="small" :disabled="quotaAtLimit || cancelBanned" @click="openCreate()">
            + 新建预约
          </el-button>
        </span>
      </el-tooltip>
    </div>

    <!-- 取消次数超限封禁提示 -->
    <el-alert v-if="cancelBanned" type="error" :closable="false" show-icon class="quota-alert"
              title="您近 30 天取消预约次数超限，暂时无法预约设备"
              :description="`剩余解禁 ${cancelStats.remainingDays} 天（30 天内取消超过 ${cancelStats.limit} 次将被限制提交新预约；如有特殊情况请联系管理员解除）`" />

    <!-- 达到同时预约上限提示 -->
    <el-alert v-if="quotaAtLimit" type="warning" :closable="false" show-icon class="quota-alert"
              title="您已达到同时预约设备数量上限"
              description="请等待已有预约审批结束或者归还设备后再预约。已驳回、已过期、已归还的预约不占用额度。" />

    <!-- 可见域提示：加入课题组后可见同组成员的预约（所有角色统一规则） -->
    <el-alert v-if="store.isStudent" type="info" :closable="false" class="scope-tip"
              :title="myGroups.length
                ? `学生身份：日历展示您本人及同课题组内成员的预约记录（已加入 ${myGroups.length} 个课题组）`
                : '学生身份：日历仅展示您本人提交的预约记录（加入课题组后可见同组成员预约）'" />
    <!-- 教师可见域提示 -->
    <el-alert v-else-if="store.isTeacher" type="info" :closable="false" class="scope-tip"
              :title="myGroups.length
                ? `教师身份：日历展示您本人、您负责的课题组成员，以及您加入的 ${myGroups.length} 个课题组内成员的预约`
                : '教师身份：日历展示您本人及您负责的课题组成员的预约（未创建课题组时仅本人）'" />

    <!-- 月份切换 -->
    <div class="cal-header">
      <el-button @click="changeMonth(-1)"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="cal-month">{{ year }} 年 {{ month }} 月</span>
      <el-button @click="changeMonth(1)"><el-icon><ArrowRight /></el-icon></el-button>
      <el-button link type="primary" style="margin-left: 12px" @click="goToday">回到本月</el-button>
      <div class="legend">
        <span><i class="dot" style="background: #c9b4a0"></i>待审批</span>
        <span><i class="dot" style="background: #9fb8ae"></i>借用中</span>
        <span><i class="dot" style="background: #bc9494"></i>已驳回</span>
        <span class="legend-note">色块底色区分设备</span>
      </div>
      <el-button size="small" style="margin-left: 16px" :loading="exporting" @click="exportExcel">导出本月 Excel</el-button>
    </div>

    <!-- 星期表头 -->
    <div class="cal-grid cal-head-row">
      <div v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w" class="cal-head-cell">{{ w }}</div>
    </div>

    <!-- 日历主体 -->
    <div class="cal-grid">
      <div v-for="(cell, i) in cells" :key="i" class="cal-cell"
           :class="{ 'other-month': !cell.inMonth, today: cell.isToday }" @click="selectedDay = cell">
        <div class="cal-date">{{ cell.day }}</div>
        <div v-for="r in cell.items.slice(0, 3)" :key="r.id" class="cal-chip"
             :class="{ 'cal-chip--expired': r.status === 'EXPIRED' }"
             :style="{ background: eqColor(r).bg, color: eqColor(r).fg, borderColor: eqColor(r).bd }"
             :title="r.status === 'EXPIRED' ? '已过期' : ''">
          <i class="chip-dot" :style="{ background: statusDot(r.status) }"></i>{{ r.equipmentName }}
        </div>
        <div v-if="cell.items.length > 3" class="cal-more">还有 {{ cell.items.length - 3 }} 条…</div>
      </div>
    </div>

    <!-- 单日详情 -->
    <el-dialog v-model="dayVisible" :title="dayTitle" width="620px">
      <el-empty v-if="!dayItems.length" description="当天暂无预约" />
      <el-table v-else :data="dayItems" size="small">
        <el-table-column prop="equipmentName" label="设备" min-width="140" />
        <el-table-column label="时间" width="200">
          <template #default="{ row }">{{ fmtTime(row.startTime) }} ~ {{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <!-- 加入课题组后（学生同样）展示预约人，便于区分同组成员 -->
        <el-table-column v-if="!store.isStudent || myGroups.length" prop="username" label="预约人" width="96" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'primary' : 'warning'" size="small">
              {{ RESERVATION_STATUS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-tooltip :disabled="!quotaAtLimit && !cancelBanned" placement="top"
                    :content="cancelBanned ? '取消次数超限，暂时无法预约' : '已达同时预约数量上限，请等待已有预约审批结束或归还设备'">
          <span style="display:inline-block">
            <el-button type="primary" :disabled="quotaAtLimit || cancelBanned" @click="openCreate(selectedDay)">
              预约这一天
            </el-button>
          </span>
        </el-tooltip>
        <span v-if="quotaAtLimit" class="footer-quota-tip">
          当前已持有 {{ quota.activeCount }} / {{ quota.maxActive }} 条有效预约
        </span>
      </template>
    </el-dialog>

    <!-- 新建预约 -->
    <el-dialog v-model="createVisible" title="新建设备预约" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="设备" required>
          <el-select v-model="form.equipmentId" filterable placeholder="选择可预约设备" style="width: 100%">
            <el-option v-for="e in equipmentOptions" :key="e.id" :value="e.id"
                       :label="`${e.name}（${e.model || ''}）${isHighRisk(e) ? ' ⚠高危' : ''}`" />
          </el-select>
          <div v-if="selectedEquip && isHighRisk(selectedEquip)" class="risk-hint">
            ⚠ 该设备属于高危设备（{{ highRiskTags(selectedEquip).join(' / ') }}），提交前需阅读并确认《安全须知》
          </div>
          <div v-if="selectedRule" class="duration-hint" :class="{ 'duration-hint--bad': durationOut }">
            ⏱ 分类「{{ selectedEquip.category }}」单次预约时长：{{ selectedRule.minDuration }} ~ {{ selectedRule.maxDuration }} 分钟
            <span v-if="durationOut && currentDuration">（当前 {{ currentDuration }} 分钟，超出限制）</span>
          </div>
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时间段" required>
          <el-time-picker v-model="form.startTime" format="HH:mm" placeholder="开始" style="width: 48%" />
          <span style="margin: 0 4px">~</span>
          <el-time-picker v-model="form.endTime" format="HH:mm" placeholder="结束" style="width: 48%" />
        </el-form-item>
        <el-form-item label="用途" required>
          <el-input v-model="form.purpose" type="textarea" :rows="2" placeholder="实验/课题用途说明" />
        </el-form-item>
        <el-form-item v-if="store.isTeacher" label="代提交">
          <el-input v-model="form.batchUsernames" type="textarea" :rows="2"
                    placeholder="教师专属：输入本班学生账号（英文逗号分隔），将为每位学生提交相同预约；留空则为自己预约" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交预约</el-button>
      </template>
    </el-dialog>

    <!-- 设备手册阅读确认（学生提交前强制阅读） -->
    <el-dialog v-model="readVisible" title="请先阅读设备手册" width="640px" append-to-body>
      <div class="read-body">
        <p class="read-title">预约「{{ readManual?.name }}」前，请阅读该设备的操作文档与安全注意事项：</p>
        <div class="read-block">
          <div class="read-label">操作文档</div>
          <div class="read-content">{{ readManual?.content || '该设备暂未录入操作文档' }}</div>
        </div>
        <div class="read-block read-block--safety">
          <div class="read-label">安全注意事项</div>
          <div class="read-content">{{ readManual?.safetyNotes || '该设备暂未录入安全注意事项' }}</div>
        </div>
        <el-checkbox v-model="readChecked">我已完整阅读该设备手册，并理解操作步骤与安全注意事项</el-checkbox>
      </div>
      <template #footer>
        <el-button @click="readVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!readChecked" :loading="readSubmitting" @click="confirmReadAndContinue">
          确认阅读并继续
        </el-button>
      </template>
    </el-dialog>

    <!-- 高危设备安全须知（强制阅读确认） -->
    <el-dialog v-model="safetyVisible" title="⚠ 高危设备安全须知 Safety Notice" width="560px" append-to-body>
      <div class="safety-body">
        <p class="safety-title">使用「{{ selectedEquip?.name }}」前，请务必知悉以下安全规范：</p>
        <ol class="safety-list">
          <li>操作前须接受过对应设备的安全培训并考核合格，未经培训禁止独自操作。</li>
          <li>高温设备使用中不得离开现场，使用后确认断电降温；危化品配套设备操作须佩戴防护装备，在通风橱内进行。</li>
          <li>严禁违规存放化学品，发现泄漏、冒烟、异味等异常立即停止操作并上报。</li>
          <li>使用结束后恢复设备初始状态，如实填写使用记录；发生事故立即报告实验室安全员。</li>
        </ol>
        <el-checkbox v-model="safetyChecked">我已阅读并知晓安全规范（I have read and understood the safety rules）</el-checkbox>
      </div>
      <template #footer>
        <el-button @click="safetyVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!safetyChecked" :loading="submitting" @click="doSubmit">确认并提交预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { reservationCalendar, reservationCreate, reservationBatch, waitlistJoin, equipmentAvailable, exportReservations, saveBlob,
         categoryRulePublic, reservationQuota, manualDetail, manualConfirmRead, groupMine,
         reservationCancelStats,
         RESERVATION_STATUS, HIGH_RISK_TAGS } from '../api'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const today = dayjs()
const year = ref(today.year())
const month = ref(today.month() + 1)
const reservations = ref([])
const selectedDay = ref(null)
const createVisible = ref(false)
const submitting = ref(false)
const equipmentOptions = ref([])
/** 设备分类时长规则（按设备所属分类校验单次预约时长） */
const categoryRules = ref([])

/** 同时预约额度（待审批 + 借用中，上限取系统配置，默认 2） */
const quota = ref({ maxActive: 2, activeCount: 0, remaining: 2, atLimit: false })
const quotaAtLimit = computed(() => !!quota.value.atLimit)
const QUOTA_LIMIT_TEXT = '您已达到同时预约设备数量上限，请等待已有预约审批结束或者归还设备后再预约'

/** 30 天取消统计与封禁状态（超限时预约按钮置灰并提示剩余解禁天数） */
const cancelStats = ref({ count30d: 0, limit: 5, banned: false, remainingDays: 0 })
const cancelBanned = computed(() => !!cancelStats.value.banned)
const CANCEL_BAN_TEXT = () =>
  `您近 30 天取消预约次数超限，暂时无法预约设备，剩余解禁 ${cancelStats.value.remainingDays} 天`

async function loadCancelStats() {
  try {
    cancelStats.value = await reservationCancelStats()
  } catch {
    // 统计接口异常不阻断日历浏览，提交时由后端兜底拦截
  }
}

/** 我已加入的课题组（别人创建的；加入后即可看到同组成员的预约） */
const myGroups = ref([])
async function loadGroups() {
  try {
    const list = await groupMine()
    myGroups.value = Array.isArray(list) ? list : []
  } catch {
    myGroups.value = []   // 课题组接口异常不影响日历浏览
  }
}

async function loadQuota() {
  try {
    quota.value = await reservationQuota()
  } catch {
    // 额度接口异常不阻断日历浏览，提交时由后端兜底拦截
  }
}

const cells = computed(() => {
  const first = dayjs(`${year.value}-${String(month.value).padStart(2, '0')}-01`)
  // 周一为一周开始
  let start = first.subtract((first.day() + 6) % 7, 'day')
  const result = []
  for (let i = 0; i < 42; i++) {
    const d = start.add(i, 'day')
    const key = d.format('YYYY-MM-DD')
    result.push({
      key,
      day: d.date(),
      inMonth: d.month() + 1 === month.value,
      isToday: key === today.format('YYYY-MM-DD'),
      items: reservations.value.filter(r =>
        key >= dayjs(r.startTime).format('YYYY-MM-DD') && key <= dayjs(r.endTime).format('YYYY-MM-DD'))
    })
  }
  return result
})

const dayVisible = computed({
  get: () => !!selectedDay.value,
  set: v => { if (!v) selectedDay.value = null }
})
const dayItems = computed(() =>
  selectedDay.value ? reservations.value.filter(r =>
    selectedDay.value.key >= dayjs(r.startTime).format('YYYY-MM-DD') &&
    selectedDay.value.key <= dayjs(r.endTime).format('YYYY-MM-DD')) : [])
const dayTitle = computed(() =>
  selectedDay.value ? `${selectedDay.value.key} 的预约（${dayItems.value.length}）` : '')

const fmtTime = t => (t ? dayjs(t).format('MM-DD HH:mm') : '-')

const form = reactive({ equipmentId: null, date: '', startTime: null, endTime: null, purpose: '', batchUsernames: '' })

/* ---------- 高危设备安全须知 ---------- */
const safetyVisible = ref(false)
const safetyChecked = ref(false)
const selectedEquip = computed(() =>
  equipmentOptions.value.find(e => e.id === form.equipmentId) || null)
const isHighRisk = e => !!e?.tags && HIGH_RISK_TAGS.some(t => e.tags.includes(t))
const highRiskTags = e => (e?.tags || '').split(',').filter(t => HIGH_RISK_TAGS.includes(t))

/** 当前所选设备所属分类的时长规则 */
const selectedRule = computed(() => {
  const c = selectedEquip.value?.category
  if (!c) return null
  return categoryRules.value.find(r => r.category === c) || null
})
/** 当前填写的时长（分钟），未填全返回 null */
const currentDuration = computed(() => {
  if (!form.date || !form.startTime || !form.endTime) return null
  const s = dayjs(`${form.date} ${dayjs(form.startTime).format('HH:mm')}:00`)
  const e = dayjs(`${form.date} ${dayjs(form.endTime).format('HH:mm')}:00`)
  const m = e.diff(s, 'minute')
  return m > 0 ? m : null
})
/** 时长是否超出该分类限制 */
const durationOut = computed(() => {
  const r = selectedRule.value
  const d = currentDuration.value
  if (!r || d == null) return false
  return d < Number(r.minDuration) || d > Number(r.maxDuration)
})

/* ---------- 设备手册阅读确认（学生提交前强制） ---------- */
const readVisible = ref(false)
const readChecked = ref(false)
const readManual = ref(null)
const readSubmitting = ref(false)

/** 学生本人预约：若尚未确认阅读该设备手册，先弹阅读确认；返回是否可继续 */
async function ensureManualRead() {
  if (!store.isStudent || isBatchMode()) return true
  try {
    const m = await manualDetail(form.equipmentId)
    if (!m || (!m.content && !m.safetyNotes)) return true
    if (m.hasRead) return true
    readManual.value = m
    readChecked.value = false
    readVisible.value = true
    return false
  } catch {
    return true   // 手册接口异常不阻断预约，后端会做最终校验
  }
}

async function confirmReadAndContinue() {
  if (!readChecked.value) return
  readSubmitting.value = true
  try {
    await manualConfirmRead(form.equipmentId)
    readVisible.value = false
    ElMessage.success('已记录设备手册阅读确认')
    proceedSubmit()
  } catch (e) {
    ElMessage.error(e?.message || '阅读确认记录失败，请重试')
  } finally {
    readSubmitting.value = false
  }
}

/** 阅读确认后继续：高危设备再弹安全须知，其余直接提交 */
function proceedSubmit() {
  if (selectedEquip.value && isHighRisk(selectedEquip.value)) {
    safetyChecked.value = false
    safetyVisible.value = true
    return
  }
  doSubmit()
}

/** 提交入口：手册阅读确认 → 高危设备安全须知 → 提交 */
async function handleSubmit() {
  if (!form.equipmentId) return ElMessage.warning('请选择设备')
  if (!form.date || !form.startTime || !form.endTime) return ElMessage.warning('请选择日期和时间段')
  if (form.endTime <= form.startTime) return ElMessage.warning('结束时间必须晚于开始时间')
  // 30 天取消次数超限封禁（按钮已置灰，此处兜底）
  if (cancelBanned.value && !isBatchMode()) {
    return ElMessageBox.alert(CANCEL_BAN_TEXT(), '无法提交预约', {
      type: 'warning', confirmButtonText: '我知道了'
    })
  }
  // 同时预约数量上限（教师批量代提交不占用教师本人额度，不计此限）
  if (quotaAtLimit.value && !isBatchMode()) {
    return ElMessageBox.alert(QUOTA_LIMIT_TEXT, '无法提交预约', {
      type: 'warning', confirmButtonText: '我知道了'
    })
  }
  // 分类时长限制（与后端同规则，提前拦截）
  const rule = selectedRule.value
  const dur = currentDuration.value
  if (rule && dur != null && durationOut.value) {
    return ElMessage.warning(`「${selectedEquip.value.category}」类设备单次预约时长须在 `
      + `${rule.minDuration} - ${rule.maxDuration} 分钟之间（当前 ${dur} 分钟）`)
  }
  if (!(await ensureManualRead())) return
  proceedSubmit()
}

function buildPayload(acknowledged) {
  return {
    equipmentId: form.equipmentId,
    startTime: dayjs(`${form.date} ${dayjs(form.startTime).format('HH:mm')}:00`).format('YYYY-MM-DDTHH:mm:ss'),
    endTime: dayjs(`${form.date} ${dayjs(form.endTime).format('HH:mm')}:00`).format('YYYY-MM-DDTHH:mm:ss'),
    purpose: form.purpose || '实验使用',
    safetyAcknowledged: acknowledged || undefined
  }
}

/** 教师批量代提交名单 */
function batchNames() {
  return (form.batchUsernames || '').split(/[,，\s]+/).map(x => x.trim()).filter(Boolean)
}
/** 是否为教师批量代提交（额度按每位学生各自计算，不占用教师本人额度） */
function isBatchMode() {
  return store.isTeacher && batchNames().length > 0
}

/* ---------- 设备专属柔和配色：同一设备全月同色，一眼区分 ---------- */
const EQ_COLORS = [
  { bg: '#e9f0f4', fg: '#3d5a6b', bd: '#c9dbe5' }, // 雾蓝
  { bg: '#e6f0ec', fg: '#3f6d5c', bd: '#c4dcd2' }, // 玻璃青
  { bg: '#f4ede2', fg: '#7c6440', bd: '#e3d4bc' }, // 试剂琥珀
  { bg: '#eeeaf4', fg: '#5d5880', bd: '#d6d0e6' }, // 石蕊紫
  { bg: '#f4e9e9', fg: '#8a5252', bd: '#e5cdcd' }, // 灰砖红
  { bg: '#eef2e2', fg: '#5f6d38', bd: '#dbe2c2' }, // 橄榄绿
  { bg: '#e4f0f2', fg: '#2f6d7a', bd: '#c2dde2' }, // 湖青
  { bg: '#f6efe6', fg: '#7d6b4a', bd: '#e6d8c4' }, // 暖沙
  { bg: '#eceff6', fg: '#42548a', bd: '#d2d8ec' }, // 靛蓝
  { bg: '#f6ece4', fg: '#8a5638', bd: '#e8d3c2' }  // 珊瑚橙
]
/** 按设备 ID 稳定取色（无 ID 时按设备名），同一设备恒定同色 */
function eqColor(r) {
  const key = String(r.equipmentId ?? r.equipmentName ?? '')
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) % 9973
  return EQ_COLORS[h % EQ_COLORS.length]
}
/** 事项左侧小圆点：保留状态语义 */
function statusDot(s) {
  return { PENDING: '#c9b4a0', APPROVED: '#9fb8ae', CLAIMED: '#87b08f', REJECTED: '#bc9494',
           RETURNED: '#a9a6b8', CANCELLED: '#b6c0c6', EXPIRED: '#bdbdbd' }[s] || '#a9a6b8'
}

/** 导出当前月份的预约记录（学生强制仅导出本人，后端兜底） */
const exporting = ref(false)
async function exportExcel() {
  exporting.value = true
  try {
    const blob = await exportReservations(store.isStudent ? { mine: true } : { mine: false })
    saveBlob(blob, `预约记录表_${year.value}-${String(month.value).padStart(2, '0')}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

async function load() {
  const m = `${year.value}-${String(month.value).padStart(2, '0')}`
  reservations.value = await reservationCalendar(m)
}

function changeMonth(delta) {
  let m = month.value + delta
  let y = year.value
  if (m < 1) { m = 12; y-- }
  if (m > 12) { m = 1; y++ }
  year.value = y
  month.value = m
}

function goToday() {
  year.value = today.year()
  month.value = today.month() + 1
}

/** 接收看板「本月预约」卡片带来的月份参数（YYYY-MM） */
function applyRouteQuery() {
  const m = route.query.month ? String(route.query.month) : ''
  if (!/^\d{4}-\d{2}$/.test(m)) return false
  const y = Number(m.slice(0, 4))
  const mo = Number(m.slice(5, 7))
  if (!y || mo < 1 || mo > 12) return false
  const changed = y !== year.value || mo !== month.value
  year.value = y
  month.value = mo
  return changed
}

function openCreate(day) {
  // 取消次数超限封禁：不再打开新建弹窗，直接提示
  if (cancelBanned.value && !store.isTeacher) {
    ElMessageBox.alert(CANCEL_BAN_TEXT(), '无法新建预约', {
      type: 'warning', confirmButtonText: '我知道了'
    })
    return
  }
  // 已达同时预约上限：不再打开新建弹窗，直接提示（教师批量代提交按学生各自额度计算，不在此限）
  if (quotaAtLimit.value && !store.isTeacher) {
    ElMessageBox.alert(QUOTA_LIMIT_TEXT, '无法新建预约', {
      type: 'warning', confirmButtonText: '我知道了'
    })
    return
  }
  if (day) {
    if (dayjs(day.key).isBefore(today, 'day')) {
      ElMessage.warning('不能预约过去的日期')
      return
    }
    form.date = day.key
  } else {
    form.date = today.format('YYYY-MM-DD')
  }
  dayVisible.value = false
  createVisible.value = true
}

async function doSubmit() {
  if (!form.purpose || !form.purpose.trim()) {
    safetyVisible.value = false
    return ElMessage.warning('请填写实验用途/备注')
  }
  if (quotaAtLimit.value && !isBatchMode()) {
    safetyVisible.value = false
    return ElMessageBox.alert(QUOTA_LIMIT_TEXT, '无法提交预约', {
      type: 'warning', confirmButtonText: '我知道了'
    })
  }
  submitting.value = true
  try {
    // 教师批量代提交
    const names = batchNames()
    if (store.isTeacher && names.length) {
      const results = await reservationBatch({ ...buildPayload(true), usernames: names })
      // 批量代提交同样产生待审批预约，通知侧边栏刷新角标
      window.dispatchEvent(new CustomEvent('pending-count-changed'))
      ElMessageBox.alert(results.join('<br/>'), '批量提交结果', { dangerouslyUseHTMLString: true, type: 'info' })
      createVisible.value = false
      form.purpose = ''
      form.batchUsernames = ''
      await load()
      await loadQuota()
      return
    }
    await reservationCreate(buildPayload(true))
    // 通知侧边栏刷新「借用审批」待办角标（待审批数量 +1）
    window.dispatchEvent(new CustomEvent('pending-count-changed'))
    ElMessage.success('预约提交成功，等待管理员审批')
    createVisible.value = false
    safetyVisible.value = false
    form.purpose = ''
    await load()
    await loadQuota()
  } catch (e) {
    safetyVisible.value = false
    // 时段冲突 → 提供加入候补队列入口
    const msg = e?.message || ''
    if (msg.includes('已被预约') || msg.includes('候补')) {
      const equip = selectedEquip.value
      const ok = await ElMessageBox.confirm(
        `「${equip?.name || '该设备'}」此时段已被占用，是否加入候补队列？空位释放后系统将自动为您创建预约申请，并以站内消息通知您。`,
        '时段已满 · 加入候补', { confirmButtonText: '加入候补队列', cancelButtonText: '暂不', type: 'warning' }
      ).catch(() => null)
      if (ok) {
        await waitlistJoin(buildPayload(false))
        ElMessage.success('已加入候补队列，可在「我的预约」中查看')
        createVisible.value = false
      }
    }
  } finally {
    submitting.value = false
  }
}

watch([year, month], load)

watch(() => ({ ...route.query }), () => {
  if (route.path !== '/calendar') return
  // 月份变化时由 watch([year, month]) 自动重新拉取
  if (!applyRouteQuery()) load()
})

onActivated(async () => {
  // 课题组可能已变化（加入 / 退出 / 被移出），每次进入都刷新可见域提示与预约人列的展示条件
  await loadGroups()
  const monthChanged = applyRouteQuery()
  if (!monthChanged) await load()
  await loadQuota()
  await loadCancelStats()
  equipmentOptions.value = await equipmentAvailable()
  try {
    categoryRules.value = await categoryRulePublic()
  } catch {
    categoryRules.value = []   // 规则接口异常时不阻断预约流程，由后端兜底校验
  }
  // 从设备手册 / 设备名录点击"预约此设备"跳转过来：预选设备并打开弹窗
  if (route.query.equipmentId) {
    form.equipmentId = Number(route.query.equipmentId)
    openCreate()
    const rest = { ...route.query }
    delete rest.equipmentId
    router.replace({ path: '/calendar', query: rest })
  }
})
</script>

<style scoped>
.title-btn { float: right; }
.quota-chip {
  display: inline-block;
  margin-left: 12px;
  padding: 3px 11px;
  border-radius: 20px;
  font-size: 12.5px;
  font-weight: 500;
  letter-spacing: 0.2px;
  vertical-align: middle;
  color: #5f6d55;
  background: #f0f3ea;
  border: 1px solid #dfe5cf;
}
.quota-chip--full {
  color: #8a5252;
  background: #f7ecec;
  border-color: #ecd4d4;
}
.quota-alert { margin-bottom: 12px; border-radius: 10px; }
.footer-quota-tip { margin-left: 12px; font-size: 12.5px; color: #8a5252; }
.scope-tip { margin-bottom: 12px; border-radius: 10px; }
.risk-hint {
  width: 100%;
  margin-top: 6px;
  font-size: 12.5px;
  font-weight: 500;
  color: #8a5252;
  background: #f7ecec;
  border: 1px solid #ecd4d4;
  border-radius: 8px;
  padding: 7px 10px;
  line-height: 1.5;
}
.duration-hint {
  width: 100%;
  margin-top: 6px;
  font-size: 12.5px;
  color: #5f6d55;
  background: #f0f3ea;
  border: 1px solid #dfe5cf;
  border-radius: 8px;
  padding: 7px 10px;
  line-height: 1.5;
}
.duration-hint--bad {
  color: #9c6a3a;
  background: #fbf1e4;
  border-color: #ecd8bb;
}
.safety-body { font-size: 14px; color: #333333; line-height: 1.7; }
.safety-title { font-weight: 600; margin-bottom: 10px; }
.safety-list { margin: 0 0 14px 18px; }
.safety-list li { margin-bottom: 8px; }
.cal-header { display: flex; align-items: center; margin-bottom: 14px; }
.cal-month { font-size: 16px; font-weight: 600; margin: 0 14px; min-width: 110px; text-align: center; color: #333333; letter-spacing: 0.4px; }
.legend { margin-left: auto; display: flex; gap: 18px; font-size: 12.5px; font-weight: 500; color: #666666; }
.legend .dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; margin-right: 5px; }
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6px; }
.cal-head-cell { text-align: center; color: #666666; font-size: 13px; font-weight: 600; padding: 8px 0; letter-spacing: 0.5px; }
.cal-cell {
  min-height: 96px;
  border: 1px solid #e6edf0;
  background: #ffffff;
  border-radius: 10px;
  padding: 7px;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}
.cal-cell:hover { border-color: #ccd9e0; background: #f7fafb; }
.cal-cell.other-month { background: #f5f8f9; border-color: #eff4f6; color: #a3adb3; }
.cal-cell.today { border-color: #8fa9bc; background: #eef4f8; }
.cal-date { font-size: 13.5px; font-weight: 700; margin-bottom: 6px; color: #333333; }
.cal-cell.other-month .cal-date { color: #a3adb3; }
.cal-cell.today .cal-date { color: #64798a; }
.cal-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid transparent;
  border-radius: 5px;
  padding: 3px 6px;
  margin-bottom: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 已过期预约：置灰 + 降低透明度，与有效预约区分 */
.cal-chip--expired {
  opacity: 0.5;
  filter: grayscale(1);
  text-decoration: line-through;
  text-decoration-thickness: 1px;
}
.chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.legend-note { color: #777777; font-size: 12px; margin-left: 4px; }
.cal-more { font-size: 12px; font-weight: 500; color: #666666; }

/* 设备手册阅读确认弹窗（莫兰迪浅色系，长文本可滚动） */
.read-body { font-size: 14px; color: #333333; }
.read-title { font-weight: 600; margin-bottom: 12px; line-height: 1.6; }
.read-block { margin-bottom: 12px; }
.read-label {
  font-size: 13px;
  font-weight: 700;
  color: #4b5a52;
  margin-bottom: 6px;
}
.read-content {
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-size: 13.5px;
  color: #3c3c3c;
  background: #f8fafb;
  border: 1px solid #e6edf0;
  border-radius: 8px;
  padding: 10px 12px;
}
.read-block--safety .read-label { color: #6b5c42; }
.read-block--safety .read-content {
  background: #f6f2ea;
  border-color: #e3dbc9;
  color: #564e3e;
}
</style>
