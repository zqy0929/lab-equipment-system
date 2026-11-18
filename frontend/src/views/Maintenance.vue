<template>
  <div class="maint-page">
    <div class="page-card">
      <div class="page-title">
        设备维护管理
        <span class="title-en">Maintenance</span>
        <el-button type="primary" class="add-btn" :icon="Plus" @click="openDialog()">新增仪器</el-button>
      </div>
      <div class="page-sub">新增仪器、维护设备基础信息、切换设备运行状态（正常 / 维修中 / 停用）</div>

      <!-- 状态概览 -->
      <div class="stat-row">
        <div v-for="s in stats" :key="s.key" class="stat-card" :class="`stat-${s.key}`">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>

      <!-- 筛选 -->
      <div class="toolbar">
        <el-input v-model="query.keyword" class="kw" placeholder="搜索设备名称 / 型号 / 存放位置" clearable
                  :prefix-icon="Search" @input="load" />
        <el-select v-model="query.category" class="sel" placeholder="全部分类" clearable @change="load">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="query.status" class="sel" placeholder="全部状态" clearable @change="load">
          <el-option v-for="(label, key) in EQUIPMENT_STATUS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button @click="resetQuery">重置</el-button>
        <span class="count-tip">共 {{ total }} 台设备</span>
      </div>

      <el-table :data="list" v-loading="loading" size="default" class="maint-table">
        <el-table-column label="设备" min-width="200">
          <template #default="{ row }">
            <div class="equip-cell">
              <span class="equip-name">{{ row.name }}</span>
              <span class="equip-model">{{ row.model || '型号待补' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="110">
          <template #default="{ row }">{{ row.category || '未分类' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="存放位置" width="130">
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </el-table-column>
        <el-table-column label="运行状态" width="150">
          <template #default="{ row }">
            <el-select :model-value="row.status" size="small" class="status-select"
                       :class="`status-${row.status}`" @change="v => changeStatus(row, v)">
              <el-option v-for="(label, key) in EQUIPMENT_STATUS" :key="key" :label="label" :value="key" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="170">
          <template #default="{ row }">
            <div class="tag-wrap">
              <el-tag v-for="t in splitTags(row.tags)" :key="t" size="small" effect="light"
                      :type="HIGH_RISK_TAGS.includes(t) ? 'danger' : 'info'">{{ t }}</el-tag>
              <span v-if="!splitTags(row.tags).length" class="muted">无标签</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑信息</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="暂无匹配的设备" :image-size="70" />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备基础信息' : '新增仪器'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="form.name" placeholder="如：高效液相色谱仪" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" placeholder="如：Agilent 1260" />
        </el-form-item>
        <el-form-item label="设备分类" prop="category">
          <el-select v-model="form.category" filterable allow-create default-first-option
                     placeholder="选择或输入分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input v-model="form.location" placeholder="如：实验楼 A201" />
        </el-form-item>
        <el-form-item label="运行状态">
          <el-radio-group v-model="form.status">
            <el-radio-button v-for="(label, key) in EQUIPMENT_STATUS" :key="key" :value="key">
              {{ label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设备标签">
          <el-select v-model="form.tagList" multiple placeholder="可多选" style="width: 100%">
            <el-option v-for="t in EQUIPMENT_TAGS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="设备用途、注意事项等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { equipmentPage, equipmentCategories, equipmentAdd, equipmentUpdate, equipmentStatusUpdate,
         EQUIPMENT_STATUS, EQUIPMENT_TAGS, HIGH_RISK_TAGS } from '../api'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const allList = ref([])
const total = ref(0)
const categories = ref([])
const query = reactive({ keyword: '', category: '', status: '' })

const stats = computed(() => [
  { key: 'all', label: '设备总数', value: allList.value.length },
  { key: 'available', label: '正常运行', value: allList.value.filter(e => e.status === 'AVAILABLE').length },
  { key: 'repair', label: '维修中', value: allList.value.filter(e => e.status === 'UNDER_REPAIR').length },
  { key: 'stopped', label: '停用', value: allList.value.filter(e => e.status === 'SCRAPPED').length }
])

function splitTags(tags) {
  return (tags || '').split(',').map(t => t.trim()).filter(Boolean)
}

async function load() {
  loading.value = true
  try {
    const [page, all] = await Promise.all([
      equipmentPage({ ...query, page: 1, size: 200 }),
      equipmentPage({ page: 1, size: 500 })
    ])
    list.value = page.records || []
    total.value = Number(page.total || list.value.length)
    allList.value = all.records || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.category = ''
  query.status = ''
  load()
}

/* ---------- 新增 / 编辑 ---------- */
const dialogVisible = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, name: '', model: '', category: '', location: '',
                        status: 'AVAILABLE', description: '', tagList: [] })
const rules = {
  name: [{ required: true, message: '请填写设备名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择或输入设备分类', trigger: 'change' }]
}

function openDialog(row) {
  Object.assign(form, {
    id: row?.id ?? null,
    name: row?.name ?? '',
    model: row?.model ?? '',
    category: row?.category ?? '',
    location: row?.location ?? '',
    status: row?.status ?? 'AVAILABLE',
    description: row?.description ?? '',
    tagList: splitTags(row?.tags)
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      id: form.id,
      name: form.name.trim(),
      model: form.model,
      category: form.category,
      location: form.location,
      status: form.status,
      description: form.description,
      tags: form.tagList.join(',')
    }
    if (form.id) {
      await equipmentUpdate(payload)
      ElMessage.success('设备信息已更新')
    } else {
      await equipmentAdd(payload)
      ElMessage.success('仪器已新增，并自动预置设备手册')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

/* ---------- 状态切换 ---------- */
async function changeStatus(row, status) {
  if (!status || status === row.status) return
  const label = EQUIPMENT_STATUS[status]
  const tip = status === 'AVAILABLE'
    ? '切换为正常后，该设备将重新开放预约。'
    : `切换为「${label}」后，该设备将不可被预约，已存在的预约记录不受影响。`
  const ok = await ElMessageBox.confirm(`确定将「${row.name}」的运行状态切换为「${label}」吗？${tip}`,
    '切换设备状态', { type: 'warning', confirmButtonText: '确定切换', cancelButtonText: '取消' }).catch(() => null)
  if (!ok) return
  try {
    await equipmentStatusUpdate(row.id, status)
    row.status = status
    ElMessage.success(`「${row.name}」已切换为${label}`)
    load()
  } catch (e) {
    ElMessage.error(e?.message || '状态切换失败')
  }
}

onActivated(async () => {
  await load()
  try { categories.value = await equipmentCategories() } catch { /* 分类接口异常不阻断 */ }
})
</script>

<style scoped>
.title-en {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #8c9298;
  letter-spacing: 1px;
  text-transform: uppercase;
}
.add-btn { float: right; }
.page-sub { font-size: 12.5px; color: #6f767c; margin: -4px 0 14px; }

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  border-radius: 12px;
  padding: 12px 16px;
  border: 1px solid transparent;
}
.stat-value { font-size: 22px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 12.5px; font-weight: 500; margin-top: 2px; }
.stat-all { background: #eef2f5; border-color: #dde6ec; color: #3f5666; }
.stat-available { background: #e9f0ec; border-color: #d2e2d9; color: #3f6d5c; }
.stat-repair { background: #f6eee3; border-color: #e8dac6; color: #7c6440; }
.stat-stopped { background: #f1eff2; border-color: #e0dce4; color: #5d5880; }

.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar .kw { width: 260px; }
.toolbar .sel { width: 150px; }
.count-tip { margin-left: auto; font-size: 12.5px; color: #6f767c; }

.equip-cell { display: flex; flex-direction: column; gap: 2px; }
.equip-name { font-size: 14px; font-weight: 600; color: #2f3a36; }
.equip-model { font-size: 12px; color: #737b80; }
.tag-wrap { display: flex; flex-wrap: wrap; gap: 4px; }
.muted { color: #9aa1a6; font-size: 12.5px; }

.status-select { width: 112px; }
/* 状态颜：正常灰绿 / 维修暖沙 / 停用灰紫 */
.status-AVAILABLE :deep(.el-select__wrapper) { background: #ecf3ee; box-shadow: 0 0 0 1px #cfdfd5 inset; }
.status-AVAILABLE :deep(.el-select__selected-item) { color: #3f6d5c; font-weight: 600; }
.status-UNDER_REPAIR :deep(.el-select__wrapper) { background: #f8f1e7; box-shadow: 0 0 0 1px #e6d6c0 inset; }
.status-UNDER_REPAIR :deep(.el-select__selected-item) { color: #7c6440; font-weight: 600; }
.status-SCRAPPED :deep(.el-select__wrapper) { background: #f2f0f4; box-shadow: 0 0 0 1px #ded9e3 inset; }
.status-SCRAPPED :deep(.el-select__selected-item) { color: #5d5880; font-weight: 600; }

.maint-table { border-radius: 10px; }
</style>
