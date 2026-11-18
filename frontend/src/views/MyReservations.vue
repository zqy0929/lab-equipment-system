<template>
  <div class="page-card">
    <div class="page-title">
      我的预约
      <el-radio-group v-model="tab" style="float: right" @change="onTabChange">
        <el-radio-button value="reservations">预约记录</el-radio-button>
        <el-radio-button value="waitlist">我的候补</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ============ 个人预约时间线 ============ -->
    <div class="timeline-card">
      <div class="timeline-head" @click="tlOpen = !tlOpen">
        <span class="tl-title">个人预约时间线 <span class="tl-en">Booking Timeline</span></span>
        <div class="tl-head-ops">
          <el-radio-group v-if="tlOpen && timeline.length" v-model="tlOrder" size="small" @click.stop
                          @change="sortTimeline">
            <el-radio-button value="desc">最新在前</el-radio-button>
            <el-radio-button value="asc">时间正序</el-radio-button>
          </el-radio-group>
          <el-button size="small" link type="primary" @click.stop="tlOpen = !tlOpen">
            {{ tlOpen ? '收起' : `展开（${timeline.length}）` }}
          </el-button>
        </div>
      </div>
      <div v-if="tlOpen" class="timeline-body" v-loading="tlLoading">
        <el-timeline v-if="tlVisible.length">
          <el-timeline-item v-for="r in tlVisible" :key="r.id"
                            :type="tlDotType(r.status)" :hollow="false"
                            :timestamp="`${fmt(r.startTime)} ~ ${fmt(r.endTime)}`" placement="top">
            <div class="tl-card" :class="{ 'tl-card--ended': ['RETURNED', 'CANCELLED', 'EXPIRED', 'AUTO_RELEASED', 'REJECTED'].includes(r.status) }">
              <div class="tl-row">
                <span class="tl-equip">{{ r.equipmentName }}</span>
                <el-tag :type="statusType(r.status)" size="small">{{ RESERVATION_STATUS[r.status] }}</el-tag>
                <span class="tl-time">提交于 {{ fmt(r.createTime) }}</span>
              </div>
              <!-- 状态流转：提交 → 审批 → 领用 → 完成 -->
              <div class="tl-flow">
                <template v-for="(step, i) in tlSteps(r)" :key="step.label">
                  <span class="flow-step" :class="step.state">
                    <i class="flow-dot"></i>{{ step.label }}
                  </span>
                  <span v-if="i < tlSteps(r).length - 1" class="flow-line" :class="{ 'flow-line--done': step.state === 'done' }"></span>
                </template>
              </div>
              <div class="tl-sub">
                <span v-if="r.status === 'REJECTED'" class="reject-text">驳回理由：{{ r.rejectReason || '-' }}</span>
                <span v-else-if="r.status === 'EXPIRED'" class="reject-text">超过结束时间未完成，已自动过期并释放时段</span>
                <span v-else-if="r.approverName">审批人：{{ r.approverName }}</span>
                <span v-if="r.purpose" class="tl-purpose">用途：{{ r.purpose }}</span>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!tlLoading" description="暂无预约记录，快去预约日历提交第一个预约吧" :image-size="70" />
        <el-button v-if="timeline.length > TL_PREVIEW" link type="primary" class="tl-more"
                   @click="tlExpand = !tlExpand">
          {{ tlExpand ? '收起早期记录' : `查看全部 ${timeline.length} 条` }}
        </el-button>
      </div>
    </div>

    <div class="toolbar" v-if="tab === 'reservations'">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option v-for="(label, key) in RESERVATION_STATUS" :key="key" :label="label" :value="key" />
      </el-select>
      <div style="flex: 1"></div>
      <el-button @click="exportExcel" :loading="exporting">导出 Excel</el-button>
    </div>
    <!-- 取消规则提示：仅在有有效预约时展示；已过期预约不显示该提示 -->
    <el-alert v-if="showCancelTip" type="warning" :closable="false" class="cancel-tip"
              title="预约开始前 120 分钟内不可取消；已通过但未按时使用、或超过结束时间仍未归还的预约，将自动标记为【已过期】并扣除信用分" />

    <!-- ============ 预约记录 ============ -->
    <template v-if="tab === 'reservations'">
      <el-table :data="list" v-loading="loading" :row-class-name="rowClass">
        <el-table-column prop="equipmentName" label="设备" min-width="140" />
        <el-table-column prop="equipmentLocation" label="位置" width="120" />
        <el-table-column label="借用时间" min-width="270">
          <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途/备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批/驳回" min-width="120">
          <template #default="{ row }">
            <span v-if="row.status === 'REJECTED'" class="reject-text">{{ row.rejectReason }}</span>
            <span v-else-if="row.approverName">审批人：{{ row.approverName }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canClaim(row)" size="small" type="success" @click="openPhoto(row, 'claim')">领用确认</el-button>
            <el-button v-if="['CLAIMED'].includes(row.status)" size="small" type="primary" @click="openPhoto(row, 'return')">归还</el-button>
            <el-button v-if="['APPROVED'].includes(row.status)" size="small" type="primary" link @click="openPhoto(row, 'return')">归还</el-button>
            <el-button v-if="row.status === 'PENDING'" size="small" link type="primary" @click="openModify(row)">改时间</el-button>
            <el-button v-if="['PENDING', 'APPROVED', 'CLAIMED'].includes(row.status)" size="small" type="danger" link
                       @click="doCancel(row)">取消</el-button>
            <el-button size="small" link type="info" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                     :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
    </template>

    <!-- ============ 我的候补 ============ -->
    <template v-else>
      <el-table :data="waitlist" v-loading="waitLoading">
        <el-table-column prop="equipmentName" label="设备" min-width="150" />
        <el-table-column label="候补时段" min-width="270">
          <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="{ WAITING: 'warning', NOTIFIED: 'success', FULFILLED: 'primary', PROMOTED: 'primary', CANCELLED: 'info' }[row.status]"
                    size="small">
              {{ { WAITING: '排队中', NOTIFIED: '已获资格', FULFILLED: '已预约', PROMOTED: '已自动转正', CANCELLED: '已取消' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="加入时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'WAITING'" size="small" type="danger" link @click="doCancelWait(row)">取消候补</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                     :total="waitTotal" :page-size="size" v-model:current-page="waitPage" @current-change="loadWaitlist" />
    </template>
  </div>

  <!-- 领用 / 归还照片上传 -->
  <el-dialog v-model="photoVisible" :title="photoType === 'claim' ? '领用确认 Claim Confirmation' : '设备归还 Return'"
             width="460px">
    <el-alert v-if="photoType === 'claim'" type="info" :closable="false" style="margin-bottom: 14px"
              title="领用照片为必填项：请拍摄设备借用前状态照片（外观、接口、附件等），作为归还核验依据" />
    <el-alert v-else type="info" :closable="false" style="margin-bottom: 14px"
              title="归还照片为必填项：请拍摄设备归还时的状态照片，供管理员核验；如发现损坏将联动损耗记录" />
    <el-upload drag :show-file-list="false" accept=".png,.jpg,.jpeg,.webp" :auto-upload="false"
               :on-change="onPhotoPick" class="photo-upload">
      <div v-if="photoPreview">
        <img :src="photoPreview" class="photo-preview" alt="preview" />
      </div>
      <div v-else>
        <el-icon size="36" color="#8fa9bc"><Plus /></el-icon>
        <div class="el-upload__text">拍摄或选择现场照片（必填，≤10MB）</div>
      </div>
    </el-upload>
    <template #footer>
      <el-button @click="photoVisible = false">取消</el-button>
      <el-button type="primary" :loading="photoSubmitting" @click="submitPhoto">
        {{ photoType === 'claim' ? '确认领用' : '确认归还' }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 修改预约时段（仅待审批） -->
  <el-dialog v-model="modifyVisible" title="修改预约时段 Modify Time" width="460px">
    <el-alert type="warning" :closable="false" style="margin-bottom: 14px"
              title="仅待审批状态的预约可修改时段；提交后将重新等待审批" />
    <el-form label-width="80px">
      <el-form-item label="设备">
        <span style="font-weight: 600">{{ modifyRow?.equipmentName }}</span>
      </el-form-item>
      <el-form-item label="新日期">
        <el-date-picker v-model="modifyForm.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="新时段">
        <el-time-picker v-model="modifyForm.startTime" format="HH:mm" placeholder="开始" style="width: 48%" />
        <span style="margin: 0 4px">~</span>
        <el-time-picker v-model="modifyForm.endTime" format="HH:mm" placeholder="结束" style="width: 48%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="modifyVisible = false">取消</el-button>
      <el-button type="primary" :loading="modifySubmitting" @click="submitModify">保存修改</el-button>
    </template>
  </el-dialog>

  <!-- 预约详情（含领用/归还照片、驳回原因） -->
  <el-dialog v-model="detailVisible" title="预约详情 Detail" width="560px">
    <template v-if="detailRow">
      <el-descriptions :column="2" border size="default">
        <el-descriptions-item label="设备">{{ detailRow.equipmentName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detailRow.status)" size="small">{{ RESERVATION_STATUS[detailRow.status] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="借用时间" :span="2">{{ fmt(detailRow.startTime) }} ~ {{ fmt(detailRow.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="用途/备注" :span="2">{{ detailRow.purpose || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ detailRow.approverName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="驳回理由">
          <span :class="{ 'reject-text': detailRow.status === 'REJECTED' }">{{ detailRow.rejectReason || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="领用时间">{{ detailRow.claimTime ? fmt(detailRow.claimTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="归还时间">{{ detailRow.returnTime ? fmt(detailRow.returnTime) : '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="detail-photos">
        <div class="photo-block">
          <div class="photo-label">领用照片（借用前状态）<span class="photo-zoom-tip">点击图片可放大</span></div>
          <el-image v-if="detailPhotos.claim" :src="detailPhotos.claim" :preview-src-list="[detailPhotos.claim]"
                    preview-teleported hide-on-click-modal class="photo-img" fit="cover" alt="claim" />
          <el-empty v-else description="未上传" :image-size="48" />
        </div>
        <div class="photo-block">
          <div class="photo-label">归还照片（供核验）<span class="photo-zoom-tip">点击图片可放大</span></div>
          <el-image v-if="detailPhotos.return" :src="detailPhotos.return" :preview-src-list="[detailPhotos.return]"
                    preview-teleported hide-on-click-modal class="photo-img" fit="cover" alt="return" />
          <el-empty v-else description="未上传" :image-size="48" />
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { reservationMy, reservationCancel, reservationReturn,  reservationClaim,
         reservationModify, reservationPhotoUrl, waitlistMy, waitlistCancel, exportReservations, saveBlob,
         RESERVATION_STATUS } from '../api'

const route = useRoute()
const router = useRouter()

/** tab 由路由 query 决定（看板「候补排队中」卡片 → tab=waitlist） */
function tabFromQuery() {
  return route.query.tab === 'waitlist' ? 'waitlist' : 'reservations'
}
const tab = ref(tabFromQuery())
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')

/* ---------------- 候补 ---------------- */
const waitLoading = ref(false)
const waitlist = ref([])
const waitTotal = ref(0)
const waitPage = ref(1)

/* ---------------- 个人预约时间线 ---------------- */
const TL_PREVIEW = 6
const tlOpen = ref(true)
const tlExpand = ref(false)
const tlOrder = ref('desc')
const tlLoading = ref(false)
const timeline = ref([])

const tlVisible = computed(() => (tlExpand.value ? timeline.value : timeline.value.slice(0, TL_PREVIEW)))

function sortTimeline() {
  timeline.value = [...timeline.value].sort((a, b) => {
    const da = dayjs(tlOrder.value === 'desc' ? b.startTime : a.startTime).valueOf()
    const db = dayjs(tlOrder.value === 'desc' ? a.startTime : b.startTime).valueOf()
    return da - db
  })
}

async function loadTimeline() {
  tlLoading.value = true
  try {
    // 一次拉取本人全部预约（size=100 足够覆盖常规用量）
    const data = await reservationMy({ page: 1, size: 100 })
    timeline.value = data.records || []
    sortTimeline()
  } finally {
    tlLoading.value = false
  }
}

/** 时间线节点颜色：进行中雾蓝、成功玻璃青、异常灰砖红、结束灰紫 */
function tlDotType(status) {
  return ({ PENDING: 'warning', APPROVED: 'primary', CLAIMED: 'success',
            REJECTED: 'danger', EXPIRED: 'danger', AUTO_RELEASED: 'danger',
            RETURNED: 'info', CANCELLED: 'info' }[status] || 'info')
}

/** 状态流转步骤：提交 → 审批 → 领用 → 完成（done=已达成 / doing=进行中 / fail=被驳回 / skip=未到达） */
function tlSteps(r) {
  const base = [
    { label: '提交', state: 'done' },
    { label: '审批', state: 'skip' },
    { label: '领用', state: 'skip' },
    { label: '完成', state: 'skip' }
  ]
  switch (r.status) {
    case 'PENDING':
      base[1] = { label: '审批', state: 'doing' }
      break
    case 'APPROVED':
      base[1] = { label: '审批', state: 'done' }
      base[2] = { label: '领用', state: 'doing' }
      break
    case 'CLAIMED':
      base[1] = { label: '审批', state: 'done' }
      base[2] = { label: '领用', state: 'done' }
      base[3] = { label: '完成', state: 'doing' }
      break
    case 'RETURNED':
      base[1] = { label: '审批', state: 'done' }
      base[2] = { label: '领用', state: 'done' }
      base[3] = { label: '完成', state: 'done' }
      break
    case 'REJECTED':
      base[1] = { label: '驳回', state: 'fail' }
      break
    case 'CANCELLED':
      base[1] = { label: '已取消', state: 'fail' }
      break
    case 'EXPIRED':
      base[1] = { label: '审批', state: 'done' }
      base[2] = { label: '已过期', state: 'fail' }
      break
    case 'AUTO_RELEASED':
      base[1] = { label: '已释放', state: 'fail' }
      break
  }
  return base
}

/** 导出当前筛选条件下的预约记录 */
const exporting = ref(false)
async function exportExcel() {
  exporting.value = true
  try {
    const blob = await exportReservations({ status: status.value || undefined, mine: true })
    saveBlob(blob, `我的预约记录_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

const fmt = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')
const statusType = s =>
  ({ PENDING: 'warning', APPROVED: 'primary', CLAIMED: 'success', REJECTED: 'danger', RETURNED: 'info',
     CANCELLED: 'info', AUTO_RELEASED: 'danger', EXPIRED: 'info' }[s] || 'info')

/** 过期预约整行置灰，与有效预约区分 */
const rowClass = ({ row }) => (row.status === 'EXPIRED' ? 'row-expired' : '')

/** 取消规则提示：仅在存在有效预约时展示，已过期场景不显示 */
const showCancelTip = computed(() => {
  if (tab.value !== 'reservations') return false
  if (status.value === 'EXPIRED') return false
  return list.value.some(r => ['PENDING', 'APPROVED', 'CLAIMED'].includes(r.status))
})

async function load() {
  if (tab.value === 'waitlist') return loadWaitlist()
  loading.value = true
  try {
    const data = await reservationMy({ status: status.value, page: page.value, size })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadWaitlist() {
  waitLoading.value = true
  try {
    const data = await waitlistMy({ page: waitPage.value, size })
    waitlist.value = data.records || []
    waitTotal.value = Number(data.total || 0)
  } finally {
    waitLoading.value = false
  }
}

/* ---------------- 领用确认 / 归还（含照片） ---------------- */
const photoVisible = ref(false)
const photoType = ref('claim')
const photoRow = ref(null)
const photoFile = ref(null)
const photoPreview = ref('')
const photoSubmitting = ref(false)

/** 领用确认窗口：开始后 30 分钟内可点 */
function canClaim(row) {
  if (row.status !== 'APPROVED') return false
  const now = dayjs()
  return !now.isBefore(dayjs(row.startTime)) && now.isBefore(dayjs(row.startTime).add(35, 'minute'))
}

function openPhoto(row, type) {
  photoRow.value = row
  photoType.value = type
  photoFile.value = null
  photoPreview.value = ''
  photoVisible.value = true
}

function onPhotoPick(file) {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('照片不能超过 10MB')
    return false
  }
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.raw?.type)) {
    ElMessage.warning('仅支持 png/jpg/webp 格式')
    return false
  }
  photoFile.value = file.raw
  photoPreview.value = URL.createObjectURL(file.raw)
  return false
}

async function submitPhoto() {
  // 领用/归还照片必填：未上传时拦截提交并弹出莫兰迪提示
  if (!photoFile.value) {
    ElMessageBox.alert(
      photoType.value === 'claim' ? '请上传仪器领用前实拍照片' : '请上传仪器归还核验实拍照片',
      '提示',
      {
        confirmButtonText: '知道了',
        customClass: 'photo-required-box',
        closeOnClickModal: false
      }
    ).catch(() => {})
    return
  }
  photoSubmitting.value = true
  try {
    const fd = new FormData()
    if (photoFile.value) fd.append('photo', photoFile.value)
    if (photoType.value === 'claim') {
      await reservationClaim(photoRow.value.id, fd)
      ElMessage.success('领用确认成功，设备进入使用中状态')
    } else {
      await reservationReturn(photoRow.value.id, fd)
      ElMessage.success('归还成功，等待管理员核验')
    }
    photoVisible.value = false
    load()
  } finally {
    photoSubmitting.value = false
  }
}

/* 归还必须上传照片：原「无照片快速归还」路径已下线，统一走照片上传弹窗 */

/* ---------------- 修改时段（仅待审批） ---------------- */
const modifyVisible = ref(false)
const modifyRow = ref(null)
const modifySubmitting = ref(false)
const modifyForm = ref({ date: '', startTime: null, endTime: null })

function openModify(row) {
  modifyRow.value = row
  modifyForm.value = {
    date: dayjs(row.startTime).format('YYYY-MM-DD'),
    startTime: dayjs(row.startTime).toDate(),
    endTime: dayjs(row.endTime).toDate()
  }
  modifyVisible.value = true
}

async function submitModify() {
  const f = modifyForm.value
  if (!f.date || !f.startTime || !f.endTime) return ElMessage.warning('请选择完整的时间段')
  if (f.endTime <= f.startTime) return ElMessage.warning('结束时间必须晚于开始时间')
  modifySubmitting.value = true
  try {
    await reservationModify({
      id: modifyRow.value.id,
      startTime: dayjs(`${f.date} ${dayjs(f.startTime).format('HH:mm')}:00`).format('YYYY-MM-DDTHH:mm:ss'),
      endTime: dayjs(`${f.date} ${dayjs(f.endTime).format('HH:mm')}:00`).format('YYYY-MM-DDTHH:mm:ss')
    })
    ElMessage.success('修改成功，等待重新审批')
    modifyVisible.value = false
    load()
  } finally {
    modifySubmitting.value = false
  }
}

/* ---------------- 取消 / 候补取消 / 详情 ---------------- */
async function doCancel(row) {
  await ElMessageBox.confirm(
    '确定取消该预约吗？本次取消将计入近 30 天取消统计：'
    + '开始前 24 小时以上取消不计入；开始前 24 小时至 3 小时之间取消计 1 次；'
    + '开始前 3 小时内取消计 2 次。30 天内累计超限将暂停提交新预约（默认 7 天）。',
    '取消预约', { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' })
  const res = await reservationCancel(row.id)
  // 取消待审批预约会减少待办数量，通知侧边栏刷新角标
  window.dispatchEvent(new CustomEvent('pending-count-changed'))
  const weight = res?.weight ?? 0
  if (weight > 0) {
    ElMessage.warning(`已取消；本次取消计入 30 天取消统计 ${weight} 次，请注意取消频率`)
  } else {
    ElMessage.success('已取消（距开始 24 小时以上，不计入取消统计）')
  }
  load()
}

async function doCancelWait(row) {
  await ElMessageBox.confirm('确定取消该候补吗？', '提示', { type: 'warning' })
  await waitlistCancel(row.id)
  ElMessage.success('已取消候补')
  loadWaitlist()
}

const detailVisible = ref(false)
const detailRow = ref(null)
const detailPhotos = ref({ claim: '', return: '' })
async function openDetail(row) {
  detailRow.value = row
  detailPhotos.value = { claim: '', return: '' }
  detailVisible.value = true
  // 带 Bearer 拉取照片二进制 → objectURL
  for (const type of ['claim', 'return']) {
    if ((type === 'claim' ? row.claimPhoto : row.returnPhoto)) {
      try {
        const token = localStorage.getItem('token')
        const resp = await fetch(reservationPhotoUrl(row.id, type), {
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        })
        if (resp.ok) detailPhotos.value[type] = URL.createObjectURL(await resp.blob())
      } catch { /* 忽略加载失败 */ }
    }
  }
}

/** 切换 tab：同步 URL（replace），加载交给 route.query 的 watch 统一处理 */
function onTabChange() {
  const query = { ...route.query }
  if (tab.value === 'waitlist') query.tab = 'waitlist'
  else delete query.tab
  router.replace({ path: '/my-reservations', query })
}

// 已在「我的预约」页时，从看板再次点卡片（query 变化）同样生效
watch(() => route.query.tab, (v) => {
  if (route.path !== '/my-reservations') return
  const next = v === 'waitlist' ? 'waitlist' : 'reservations'
  if (next === tab.value) return
  tab.value = next
  load()
})

onActivated(() => {
  load()
  loadTimeline()
})
</script>

<style scoped>
.reject-text { color: #a05252; font-weight: 500; }
/* ---------- 个人预约时间线卡片 ---------- */
.timeline-card {
  border: 1px solid #e6edf0;
  border-radius: 12px;
  background: #fbfdfe;
  padding: 14px 18px 10px;
  margin-bottom: 16px;
}
.timeline-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}
.tl-title { font-size: 15px; font-weight: 700; color: #333333; letter-spacing: 0.3px; }
.tl-en { font-size: 12px; font-weight: 500; color: #8c9298; letter-spacing: 0.6px; margin-left: 6px; }
.tl-head-ops { display: flex; align-items: center; gap: 10px; }
.timeline-body { margin-top: 14px; padding-left: 4px; max-height: 560px; overflow-y: auto; }
.tl-card {
  border: 1px solid #e6edf0;
  border-radius: 10px;
  background: #ffffff;
  padding: 10px 14px;
  transition: border-color 0.2s;
}
.tl-card:hover { border-color: #b9cfdb; }
.tl-card--ended { background: #fafbfb; }
.tl-row { display: flex; align-items: center; gap: 10px; }
.tl-equip { font-size: 14.5px; font-weight: 700; color: #2f4a5c; }
.tl-time { margin-left: auto; font-size: 12px; color: #8c9298; }
/* 状态流转小步骤 */
.tl-flow { display: flex; align-items: center; gap: 6px; margin-top: 9px; flex-wrap: wrap; }
.flow-step {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 500;
  color: #9aa3a9;
}
.flow-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ccd6dc;
}
.flow-step.done { color: #5f8378; font-weight: 600; }
.flow-step.done .flow-dot { background: #9fb8ae; }
.flow-step.doing { color: #5f7e90; font-weight: 600; }
.flow-step.doing .flow-dot { background: #8fa9bc; box-shadow: 0 0 0 3px rgba(143, 169, 188, 0.25); }
.flow-step.fail { color: #a06a6a; font-weight: 600; }
.flow-step.fail .flow-dot { background: #bc9494; }
.flow-line { width: 26px; height: 2px; border-radius: 2px; background: #e2e9ed; }
.flow-line--done { background: #b8d4c6; }
.tl-sub {
  margin-top: 7px;
  display: flex;
  gap: 14px;
  font-size: 12.5px;
  color: #777777;
  flex-wrap: wrap;
}
.tl-purpose { word-break: break-all; }
.tl-more { margin: 10px auto 6px; display: block; }

.photo-upload :deep(.el-upload-dragger) { padding: 18px; }
.photo-preview { max-width: 100%; max-height: 200px; border-radius: 8px; }
.detail-photos { display: flex; gap: 14px; margin-top: 16px; }
.photo-block { flex: 1; min-width: 0; border: 1px solid #ecf1f4; border-radius: 10px; padding: 10px; background: #fbfdfe; }
.photo-label { font-size: 13px; font-weight: 600; color: #333333; margin-bottom: 8px; display: flex; align-items: baseline; justify-content: space-between; }
.photo-zoom-tip { font-size: 11.5px; font-weight: 400; color: #9aa8b0; }
.photo-img { width: 100%; border-radius: 8px; display: block; cursor: zoom-in; }
.cancel-tip { margin-top: 4px; border-radius: 10px; }
/* 已过期预约：整行置灰 + 降低对比度，与有效预约明显区分 */
:deep(.row-expired) { color: #a9a9a9; }
:deep(.row-expired td) { background: #f7f7f6 !important; }
:deep(.row-expired .el-tag) { opacity: 0.75; }
:deep(.row-expired .el-button) { color: #9a9a9a; }
</style>

<!-- 领用/归还照片必填提示弹窗（teleport 到 body，须非 scoped） -->
<style>
.photo-required-box {
  width: 420px;
  border-radius: 14px;
  border: 1px solid #eaf0f3;
  background: #ffffff;
  box-shadow: 0 18px 48px rgba(130, 145, 155, 0.18);
  padding-bottom: 6px;
}
.photo-required-box .el-message-box__header { padding: 20px 22px 6px; }
.photo-required-box .el-message-box__title {
  font-size: 15.5px;
  font-weight: 700;
  color: #333333;
  letter-spacing: 0.3px;
}
.photo-required-box .el-message-box__headerbtn { top: 16px; right: 14px; }
.photo-required-box .el-message-box__content { padding: 4px 22px 16px; }
.photo-required-box .el-message-box__message {
  font-size: 13.5px;
  color: #4a555c;
  line-height: 1.75;
}
.photo-required-box .el-message-box__status.el-icon { color: #c9b39a; }
.photo-required-box .el-message-box__btns { padding: 0 22px 14px; gap: 10px; }
.photo-required-box .el-message-box__btns .el-button {
  min-width: 92px;
  border-radius: 8px;
  font-size: 13px;
}
.photo-required-box .el-message-box__btns .el-button--primary {
  background: #8fa9bc;
  border-color: #8fa9bc;
  letter-spacing: 0.5px;
}
.photo-required-box .el-message-box__btns .el-button--primary:hover {
  background: #7d9aae;
  border-color: #7d9aae;
}
</style>
