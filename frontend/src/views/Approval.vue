<template>
  <div class="page-card">
    <div class="page-title">借用审批</div>

    <div class="tab-bar">
      <button class="tab-btn" :class="{ 'is-active': activeTab === 'approval' }" @click="activeTab = 'approval'">审批队列</button>
      <button class="tab-btn" :class="{ 'is-active': activeTab === 'cancel' }" @click="switchCancel">取消限制</button>
    </div>

    <!-- ============ 审批队列 ============ -->
    <div v-if="activeTab === 'approval'">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="load">
        <el-radio-button value="PENDING">待审批</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="APPROVED">借用中</el-radio-button>
        <el-radio-button value="CLAIMED">使用中</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
        <el-radio-button value="RETURNED">已归还</el-radio-button>
        <el-radio-button value="EXPIRED">已过期</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="list" v-loading="loading" class="approval-table" @row-click="openDetail">
      <el-table-column prop="username" label="预约人" width="100" />
      <el-table-column prop="equipmentName" label="设备" min-width="150" />
      <el-table-column label="借用时间" min-width="290">
        <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="purpose" label="用途" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approverName" label="审批人" width="90" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="info" class="detail-btn"
                     @click.stop="openDetail(row)">详情</el-button>
          <template v-if="row.status === 'PENDING'">
            <template v-if="isMine(row)">
              <el-tooltip content="不能审批自己提交的预约，请由其他设备保管员或管理员审批" placement="top">
                <span class="self-hint" @click.stop>本人提交·不可自审</span>
              </el-tooltip>
            </template>
            <template v-else>
              <el-button size="small" type="success" @click.stop="doApprove(row, true)">通过</el-button>
              <el-button size="small" type="danger" @click.stop="doApprove(row, false)">驳回</el-button>
            </template>
          </template>
          <el-button v-else-if="row.status === 'APPROVED'" size="small" type="success" link
                     @click.stop="doReturn(row)">标记归还</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
    </div>

    <!-- ============ 取消限制：封禁名单 + 全体用户取消记录 ============ -->
    <div v-else>
      <div class="section-label">预约封禁中（30 天取消超限）</div>
      <el-table :data="bans" v-loading="bansLoading" empty-text="当前没有处于封禁中的用户">
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            {{ row.realName || row.username || ('用户 #' + row.userId) }}
            <span class="ban-username" v-if="row.realName && row.username">（{{ row.username }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="近 30 天取消" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="danger">{{ row.count30d }} 次</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="封禁截止" min-width="180">
          <template #default="{ row }">{{ fmt(row.banUntil) }}</template>
        </el-table-column>
        <el-table-column label="剩余解禁" width="110">
          <template #default="{ row }">{{ row.remainingDays }} 天</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="doLift(row)">解除限制</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-label" style="margin-top: 22px">全体用户取消记录</div>
      <div class="toolbar" style="margin-bottom: 12px">
        <el-input v-model="cancelKeyword" placeholder="搜索用户名 / 姓名" clearable style="width: 220px" />
        <el-button type="primary" @click="searchCancelRecords">查询</el-button>
        <div style="flex: 1"></div>
        <span class="records-hint">仅统计用户主动取消（管理员驳回、系统自动过期不计入）</span>
      </div>
      <el-table :data="cancelRecords" v-loading="recordsLoading" empty-text="暂无取消记录">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column prop="equipmentName" label="设备" min-width="150" />
        <el-table-column label="取消时间" width="170">
          <template #default="{ row }">{{ fmt(row.cancelTime) }}</template>
        </el-table-column>
        <el-table-column label="计入次数" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.weight >= 2 ? 'danger' : 'warning'">计 {{ row.weight }} 次</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="计次说明" min-width="220" show-overflow-tooltip />
      </el-table>
      <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                     :total="cancelTotal" :page-size="cancelSize" v-model:current-page="cancelPage"
                     @current-change="loadCancelRecords" />
    </div>

    <!-- 预约详情弹窗：与【我的预约】详情完全一致（含领用/归还照片、驳回理由） -->
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
  </div>
</template>

<script setup>
import { ref, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { approvalPage, approvalSubmit, reservationReturn, reservationPhotoUrl,
         cancelRecordsPage, cancelBansList, cancelBanLift, RESERVATION_STATUS } from '../api'
import { useUserStore } from '../store/user'

const store = useUserStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('PENDING')

const fmt = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')
const statusType = s =>
  ({ PENDING: 'warning', APPROVED: 'primary', CLAIMED: 'success', REJECTED: 'danger', RETURNED: 'info',
     CANCELLED: 'info', AUTO_RELEASED: 'danger', EXPIRED: 'info' }[s] || 'info')

/** 权限隔离：管理员/设备保管员可自己预约，但不得审批自己提交的预约 */
const isMine = row => store.user && row.userId && Number(store.user.id) === Number(row.userId)

async function load() {
  loading.value = true
  try {
    const data = await approvalPage({ status: status.value, page: page.value, size })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function doApprove(row, approved) {
  let reason = '同意'
  if (!approved) {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回预约', {
      inputPlaceholder: '如：设备已被占用 / 培训未完成',
      inputValidator: v => !!v || '驳回必须填写原因'
    })
    reason = value
  }
  await approvalSubmit({ id: row.id, approved, reason })
  // 通知侧边栏刷新「借用审批」待办角标（待审批数量 -1）
  window.dispatchEvent(new CustomEvent('pending-count-changed'))
  ElMessage.success(approved ? '已通过' : '已驳回')
  load()
}

async function doReturn(row) {
  await ElMessageBox.confirm(`确认「${row.equipmentName}」已归还吗？`, '提示', { type: 'info' })
  await reservationReturn(row.id)
  ElMessage.success('已标记归还')
  load()
}

/* ---------------- 预约详情（行点击 / 详情按钮；与我的预约详情一致） ---------------- */
const detailVisible = ref(false)
const detailRow = ref(null)
const detailPhotos = ref({ claim: '', return: '' })
function openDetail(row) {
  detailRow.value = row
  detailPhotos.value = { claim: '', return: '' }
  detailVisible.value = true
  // 带 Bearer 拉取照片二进制 → objectURL（后端照片接口允许 ADMIN/KEEPER 查看）
  for (const type of ['claim', 'return']) {
    if ((type === 'claim' ? row.claimPhoto : row.returnPhoto)) {
      try {
        const token = localStorage.getItem('token')
        fetch(reservationPhotoUrl(row.id, type), {
          headers: token ? { Authorization: `Bearer ${token}` } : {}
        }).then(async resp => {
          if (resp.ok) detailPhotos.value[type] = URL.createObjectURL(await resp.blob())
        })
      } catch { /* 忽略加载失败 */ }
    }
  }
}

onActivated(() => {
  if (activeTab.value === 'approval') load()
  else loadCancelTab()
})

/* ---------------- 取消限制：封禁名单 + 全体用户取消记录 ---------------- */
const activeTab = ref('approval')

const bans = ref([])
const bansLoading = ref(false)
const cancelRecords = ref([])
const cancelTotal = ref(0)
const cancelPage = ref(1)
const cancelSize = 10
const recordsLoading = ref(false)
const cancelKeyword = ref('')

function searchCancelRecords() {
  cancelPage.value = 1
  loadCancelRecords()
}

function switchCancel() {
  activeTab.value = 'cancel'
  loadCancelTab()
}

async function loadCancelTab() {
  loadBans()
  loadCancelRecords()
}

async function loadBans() {
  bansLoading.value = true
  try {
    const list = await cancelBansList()
    // 补充用户名/姓名展示
    bans.value = Array.isArray(list) ? list : []
  } finally {
    bansLoading.value = false
  }
}

async function loadCancelRecords() {
  recordsLoading.value = true
  try {
    const data = await cancelRecordsPage({ page: cancelPage.value, size: cancelSize, keyword: cancelKeyword.value })
    cancelRecords.value = data.records || []
    cancelTotal.value = Number(data.total || 0)
  } finally {
    recordsLoading.value = false
  }
}

/** 手动解除用户预约封禁（管理员/保管员豁免权限） */
async function doLift(row) {
  const ok = await ElMessageBox.confirm(
    `确定解除该用户的预约封禁吗？解除后该用户可立即提交新的设备预约。`,
    '解除预约封禁', { type: 'warning', confirmButtonText: '确定解除', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!ok) return
  await cancelBanLift(row.userId)
  ElMessage.success('已解除该用户的预约封禁限制')
  loadCancelTab()
}
</script>

<style scoped>
/* 标签页（与信用管理页统一风格） */
.tab-bar {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
  border-bottom: 1px solid #edf2f4;
  padding-bottom: 10px;
}
.tab-btn {
  border: none;
  background: transparent;
  padding: 7px 16px;
  border-radius: 8px;
  font-size: 14px;
  color: #333333;
  cursor: pointer;
  transition: background-color 0.2s, color 0.2s;
}
.tab-btn:hover { background: #ebf1f3; color: #4a6c80; }
.tab-btn.is-active { background: #dee9ef; color: #3d5a6b; font-weight: 700; }
.section-label {
  font-size: 14px;
  font-weight: 700;
  color: #333333;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-label::before {
  content: '';
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: #8fa9bc;
}
.ban-username { font-size: 12px; color: #9aa8b0; }
.records-hint { font-size: 12.5px; color: #9aa8b0; }
/* 本人提交的预约：不可自审提示 */
.self-hint {
  display: inline-block;
  font-size: 12px;
  color: #9c8168;
  background: #f6efe6;
  border: 1px dashed #d8c3aa;
  border-radius: 6px;
  padding: 3px 8px;
  cursor: not-allowed;
}
/* 行可点击唤起详情 */
.approval-table :deep(.el-table__row) { cursor: pointer; }
.approval-table :deep(.el-table__row .el-button) { cursor: pointer; }

/* ---------- 预约详情弹窗（与我的预约统一样式） ---------- */
.reject-text { color: #a05252; font-weight: 500; }
.detail-photos { display: flex; gap: 14px; margin-top: 16px; }
.photo-block { flex: 1; min-width: 0; border: 1px solid #ecf1f4; border-radius: 10px; padding: 10px; background: #fbfdfe; }
.photo-label { font-size: 13px; font-weight: 600; color: #333333; margin-bottom: 8px; display: flex; align-items: baseline; justify-content: space-between; }
.photo-zoom-tip { font-size: 11.5px; font-weight: 400; color: #9aa8b0; }
.photo-img { width: 100%; border-radius: 8px; display: block; cursor: zoom-in; }
</style>
