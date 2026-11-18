<template>
  <div class="page-card">
    <div class="page-title">安全中心 <span class="title-en">Safety Center</span></div>

    <!-- 安全员只读提示：无任何审批 / 编辑权限，隐患处理由管理员操作 -->
    <el-alert v-if="!store.isAdmin" type="info" :closable="false" class="readonly-tip"
              title="安全员只读核查视图：仅可查看高危设备使用记录、安全须知阅读记录与隐患上报数据，隐患处理由系统管理员操作" />

    <div class="tab-row">
      <button v-for="t in tabs" :key="t.key" class="tab-btn" :class="{ 'is-active': tab === t.key }"
              @click="tab = t.key; load()">{{ t.label }}</button>
    </div>

    <!-- 高危设备使用记录 -->
    <template v-if="tab === 'usage'">
      <div class="toolbar">
        <el-radio-group v-model="usageStatus" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="PENDING">待审批</el-radio-button>
          <el-radio-button value="APPROVED">借用中</el-radio-button>
          <el-radio-button value="CLAIMED">使用中</el-radio-button>
          <el-radio-button value="RETURNED">已归还</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="list" v-loading="loading">
        <el-table-column prop="equipmentName" label="高危设备" min-width="150" />
        <el-table-column prop="username" label="使用人" width="110" />
        <el-table-column label="使用时间" min-width="270">
          <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 安全须知阅读记录 -->
    <template v-else-if="tab === 'reads'">
      <el-table :data="reads" v-loading="readsLoading">
        <el-table-column prop="equipmentName" label="高危设备" min-width="150" />
        <el-table-column prop="username" label="阅读人" width="120" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="createTime" label="阅读时间" width="175" />
      </el-table>
      <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                     :total="readsTotal" :page-size="size" v-model:current-page="readsPage" @current-change="loadReads" />
    </template>

    <!-- 隐患上报记录 -->
    <template v-else>
      <div class="toolbar">
        <el-radio-group v-model="hazardStatus" @change="loadHazards">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="REPORTED">待处理</el-radio-button>
          <el-radio-button value="HANDLED">已处理</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="hazards" v-loading="hazardsLoading">
        <el-table-column prop="equipmentName" label="相关设备" width="150">
          <template #default="{ row }">{{ row.equipmentName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="上报人" width="110" />
        <el-table-column prop="content" label="隐患描述" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="row.status === 'HANDLED' ? 'success' : 'danger'" size="small">
              {{ row.status === 'HANDLED' ? '已处理' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理人" width="110" />
        <el-table-column prop="handleNote" label="处理说明" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="store.isAdmin" label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'REPORTED'" size="small" type="primary" @click="openHandle(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                     :total="hazardsTotal" :page-size="size" v-model:current-page="hazardsPage" @current-change="loadHazards" />
    </template>
  </div>

  <!-- 处理隐患 -->
  <el-dialog v-model="handleVisible" title="处理安全隐患" width="460px">
    <el-form label-width="80px">
      <el-form-item label="隐患描述">
        <div class="hazard-content">{{ handleRow?.content }}</div>
      </el-form-item>
      <el-form-item label="处理说明">
        <el-input v-model="handleNote" type="textarea" :rows="3" placeholder="核验结果、整改措施等" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisible = false">取消</el-button>
      <el-button type="primary" @click="submitHandle">完成处理</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { safetyHighRisk, safetyReadRecords, safetyHazards, safetyHandleHazard, RESERVATION_STATUS } from '../api'
import { useUserStore } from '../store/user'

const store = useUserStore()

const tabs = [
  { key: 'usage', label: '高危设备使用记录' },
  { key: 'reads', label: '安全须知阅读记录' },
  { key: 'hazards', label: '安全隐患上报' }
]
const tab = ref('usage')
const size = 10

/* 高危使用记录 */
const loading = ref(false)
const list = ref([])
const usageStatus = ref('')
const fmt = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')
const statusType = s =>
  ({ PENDING: 'warning', APPROVED: 'primary', CLAIMED: 'success', REJECTED: 'danger', RETURNED: 'info',
     CANCELLED: 'info', AUTO_RELEASED: 'danger' }[s] || 'info')

/* 阅读记录 */
const reads = ref([])
const readsLoading = ref(false)
const readsTotal = ref(0)
const readsPage = ref(1)

/* 隐患上报 */
const hazards = ref([])
const hazardsLoading = ref(false)
const hazardsTotal = ref(0)
const hazardsPage = ref(1)
const hazardStatus = ref('')
const handleVisible = ref(false)
const handleRow = ref(null)
const handleNote = ref('')

async function load() {
  if (tab.value === 'usage') return loadUsage()
  if (tab.value === 'reads') return loadReads()
  return loadHazards()
}

async function loadUsage() {
  loading.value = true
  try {
    const data = await safetyHighRisk({ status: usageStatus.value || undefined, page: 1, size: 10 })
    list.value = data.records || []
  } finally {
    loading.value = false
  }
}

async function loadReads() {
  readsLoading.value = true
  try {
    const data = await safetyReadRecords({ page: readsPage.value, size })
    reads.value = data.records || []
    readsTotal.value = Number(data.total || 0)
  } finally {
    readsLoading.value = false
  }
}

async function loadHazards() {
  hazardsLoading.value = true
  try {
    const data = await safetyHazards({ status: hazardStatus.value || undefined, page: hazardsPage.value, size })
    hazards.value = data.records || []
    hazardsTotal.value = Number(data.total || 0)
  } finally {
    hazardsLoading.value = false
  }
}

function openHandle(row) {
  handleRow.value = row
  handleNote.value = ''
  handleVisible.value = true
}

async function submitHandle() {
  await safetyHandleHazard(handleRow.value.id, { handleNote: handleNote.value })
  ElMessage.success('已处理并通知上报人')
  handleVisible.value = false
  loadHazards()
}

onActivated(load)
</script>

<style scoped>
.title-en { font-size: 12.5px; font-weight: 400; color: #777777; letter-spacing: 0.5px; margin-left: 6px; }
.readonly-tip { margin-bottom: 14px; }
.tab-row { display: flex; gap: 8px; margin-bottom: 16px; }
.tab-btn {
  border: 1px solid #dde7ec;
  background: #ffffff;
  color: #333333;
  cursor: pointer;
  border-radius: 999px;
  padding: 7px 18px;
  font-size: 13.5px;
  font-weight: 500;
  transition: all 0.2s;
}
.tab-btn:hover { background: #ebf1f3; color: #4a6c80; }
.tab-btn.is-active { background: #dee9ef; color: #3d5a6b; font-weight: 700; border-color: #a9c0cf; }
.hazard-content {
  font-size: 13.5px;
  color: #333333;
  background: #f7f9fa;
  border-radius: 8px;
  padding: 10px 12px;
  line-height: 1.6;
}
</style>
