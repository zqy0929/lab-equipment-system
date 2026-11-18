<template>
  <div class="page-card">
    <div class="page-title">损耗记录</div>
    <!-- 可见域提示 -->
    <el-alert v-if="store.isStudent" type="info" :closable="false" class="scope-tip"
              title="学生身份：此处仅展示您本人上报的损耗记录；处理进度将通过站内消息通知您" />
    <el-alert v-else-if="store.isTeacher" type="info" :closable="false" class="scope-tip"
              title="教师身份：此处仅展示您本人上报的损耗记录（损耗信息不参与课题组共享）" />
    <div class="toolbar">
      <el-radio-group v-model="status" @change="onStatusChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="REPORTED">待处理</el-radio-button>
        <el-radio-button value="FIXING">维修中</el-radio-button>
        <el-radio-button value="FIXED">已修复</el-radio-button>
      </el-radio-group>
      <div style="flex: 1"></div>
      <el-button @click="exportExcel" :loading="exporting">导出 Excel</el-button>
      <el-button type="warning" @click="openHazard">⚠ 上报安全隐患</el-button>
      <el-button type="danger" @click="openReport">⚠ 上报损耗</el-button>
    </div>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="equipmentName" label="设备" min-width="150" />
      <el-table-column prop="description" label="损耗描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="严重程度" width="96">
        <template #default="{ row }">
          <el-tag :type="{ MINOR: 'info', MODERATE: 'warning', SEVERE: 'danger' }[row.severity]" size="small">
            {{ DAMAGE_SEVERITY[row.severity] || row.severity }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reporterName" label="上报人" width="90" />
      <el-table-column label="处理状态" width="96">
        <template #default="{ row }">
          <el-tag :type="{ REPORTED: 'danger', FIXING: 'warning', FIXED: 'success' }[row.status]" size="small">
            {{ DAMAGE_STATUS[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="store.canManage" label="维修费用" width="100">
        <template #default="{ row }">{{ row.cost != null ? `¥${row.cost}` : '-' }}</template>
      </el-table-column>
      <el-table-column prop="handlerName" label="处理人" width="90" />
      <el-table-column prop="handleNote" label="处理说明" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.canManage && row.status !== 'FIXED'" size="small" type="primary"
                     @click="openHandle(row)">处理</el-button>
          <el-tooltip v-else-if="row.status !== 'FIXED'" content="报修处理由管理员 / 设备保管员负责，进度将通过站内消息通知您"
                      placement="left">
            <span class="pending-note">处理中</span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
  </div>

  <!-- 上报损耗 -->
  <el-dialog v-model="reportVisible" title="上报设备损耗" width="500px">
    <el-form label-width="80px">
      <el-form-item label="设备" required>
        <el-select v-model="reportForm.equipmentId" filterable placeholder="选择设备" style="width: 100%">
          <el-option v-for="e in allEquipment" :key="e.id" :value="e.id" :label="`${e.name}（${e.model || ''}）`" />
        </el-select>
      </el-form-item>
      <el-form-item label="严重程度">
        <el-radio-group v-model="reportForm.severity">
          <el-radio value="MINOR">轻微</el-radio>
          <el-radio value="MODERATE">中等</el-radio>
          <el-radio value="SEVERE">严重（设备将停用维修）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="描述" required>
        <el-input v-model="reportForm.description" type="textarea" :rows="3"
                  placeholder="请描述故障现象、发生时间、影响范围等" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="reportVisible = false">取消</el-button>
      <el-button type="primary" @click="submitReport">提交</el-button>
    </template>
  </el-dialog>

  <!-- 上报安全隐患 -->
  <el-dialog v-model="hazardVisible" title="上报安全隐患 Safety Hazard" width="500px">
    <el-form label-width="80px">
      <el-form-item label="相关设备">
        <el-select v-model="hazardForm.equipmentId" filterable clearable placeholder="选择相关设备（可选）" style="width: 100%">
          <el-option v-for="e in allEquipment" :key="e.id" :value="e.id" :label="`${e.name}（${e.model || ''}）`" />
        </el-select>
      </el-form-item>
      <el-form-item label="隐患描述" required>
        <el-input v-model="hazardForm.content" type="textarea" :rows="4"
                  placeholder="描述安全隐患：泄漏、冒烟、异味、线路老化、防护缺失等（将通知安全员处理）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="hazardVisible = false">取消</el-button>
      <el-button type="primary" @click="submitHazard">提交上报</el-button>
    </template>
  </el-dialog>

  <!-- 管理员处理 -->
  <el-dialog v-model="handleVisible" title="处理损耗记录" width="500px">
    <el-form label-width="90px">
      <el-form-item label="处理状态">
        <el-radio-group v-model="handleForm.status">
          <el-radio value="FIXING">维修中</el-radio>
          <el-radio value="FIXED">已修复</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="维修费用">
        <el-input-number v-model="handleForm.cost" :min="0" :precision="2" style="width: 100%" />
      </el-form-item>
      <el-form-item label="处理说明">
        <el-input v-model="handleForm.handleNote" type="textarea" :rows="3" placeholder="维修方式、更换配件等" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisible = false">取消</el-button>
      <el-button type="primary" @click="submitHandle">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { damagePage, damageReport, damageHandle, equipmentPage, exportDamages, hazardReport, saveBlob, DAMAGE_SEVERITY, DAMAGE_STATUS } from '../api'
import { useUserStore } from '../store/user'

const store = useUserStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const allEquipment = ref([])

/** 接收看板卡片带来的筛选条件：FIXING（维修中）/ REPORTED（待处理） */
const DAMAGE_FILTERS = ['REPORTED', 'FIXING', 'FIXED']
function applyRouteQuery() {
  const s = route.query.status ? String(route.query.status) : ''
  status.value = DAMAGE_FILTERS.includes(s) ? s : ''
  page.value = 1
}

/** 手动切换标签：URL 同步（replace 不产生历史记录），加载交由 route.query 的 watch 统一处理 */
function onStatusChange() {
  const query = { ...route.query }
  if (status.value) query.status = status.value
  else delete query.status
  router.replace({ path: '/damage', query })
}

const reportVisible = ref(false)
const reportForm = reactive({ equipmentId: null, severity: 'MINOR', description: '' })

/* ---------- 安全隐患上报（通知安全员） ---------- */
const hazardVisible = ref(false)
const hazardForm = reactive({ equipmentId: null, content: '' })
function openHazard() {
  Object.assign(hazardForm, { equipmentId: null, content: '' })
  hazardVisible.value = true
}
async function submitHazard() {
  if (!hazardForm.content || !hazardForm.content.trim()) return ElMessage.warning('请描述安全隐患')
  await hazardReport({ equipmentId: hazardForm.equipmentId || undefined, content: hazardForm.content })
  hazardVisible.value = false
  ElMessage.success('上报成功，已通知安全员与管理员')
}
const handleVisible = ref(false)
const handleForm = reactive({ id: null, status: 'FIXING', cost: 0, handleNote: '' })

/** 导出当前筛选条件下的损耗维修记录 */
const exporting = ref(false)
async function exportExcel() {
  exporting.value = true
  try {
    const blob = await exportDamages({ status: status.value || undefined })
    saveBlob(blob, `损耗维修记录表_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await damagePage({ status: status.value, page: page.value, size })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openReport() {
  Object.assign(reportForm, { equipmentId: null, severity: 'MINOR', description: '' })
  reportVisible.value = true
}

async function submitReport() {
  if (!reportForm.equipmentId) return ElMessage.warning('请选择设备')
  if (!reportForm.description) return ElMessage.warning('请描述损耗情况')
  await damageReport(reportForm)
  reportVisible.value = false
  ElMessage.success('上报成功，已通知管理员')
  load()
}

function openHandle(row) {
  Object.assign(handleForm, { id: row.id, status: 'FIXING', cost: 0, handleNote: '' })
  handleVisible.value = true
}

async function submitHandle() {
  await damageHandle(handleForm)
  handleVisible.value = false
  ElMessage.success('处理已保存')
  load()
}

onActivated(async () => {
  applyRouteQuery()
  load()
  const data = await equipmentPage({ page: 1, size: 200 })
  allEquipment.value = data.records || []
})

// 已在损耗记录页时，从看板再次跳转（query 变化）同样生效
watch(() => ({ ...route.query }), () => {
  if (route.path !== '/damage') return
  applyRouteQuery()
  load()
})
</script>

<style scoped>
.scope-tip { margin-bottom: 12px; border-radius: 10px; }
.pending-note {
  font-size: 12.5px;
  color: #8c9298;
  background: #f4f6f7;
  border-radius: 6px;
  padding: 3px 10px;
}
</style>
