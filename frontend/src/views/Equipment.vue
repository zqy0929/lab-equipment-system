<template>
  <div class="page-card md-page">
    <!-- 页头 -->
    <div class="md-header">
      <div>
        <div class="page-title">设备名录 <span class="title-en">Equipment Directory</span></div>
        <div class="page-sub">点击右侧卡片查看设备详情 Click a card to view details</div>
      </div>
      <div>
        <el-button @click="exportExcel" :loading="exporting">导出台账 Excel</el-button>
        <el-button v-if="store.isAdmin" type="primary" @click="openDialog()">+ 新增设备 Add</el-button>
      </div>
    </div>

    <div class="md-layout">
      <!-- ============ 主栏：设备详情 ============ -->
      <div class="md-main">
        <div class="md-main-title">设备详情 <span class="title-en">Detail</span></div>

        <template v-if="current">
          <div class="detail-top">
            <div class="detail-icon">
              <el-icon :size="30"><component :is="deviceIcon(current.name, current.category)" /></el-icon>
            </div>
            <div class="detail-name-wrap">
              <div class="detail-name">{{ current.name }}</div>
              <div class="detail-model">{{ current.model || '—' }}</div>
            </div>
            <el-tag :type="statusTagType(current.status)" size="large">
              {{ EQUIPMENT_STATUS[current.status] || current.status }}
            </el-tag>
          </div>

          <div class="detail-grid">
            <div class="detail-item">
              <div class="detail-label">分类 Category</div>
              <div class="detail-value">{{ current.category || '—' }}</div>
            </div>
            <div class="detail-item">
              <div class="detail-label">位置 Location</div>
              <div class="detail-value">{{ current.location || '—' }}</div>
            </div>
            <div class="detail-item detail-item--wide">
              <div class="detail-label">标签 Tags</div>
              <div class="detail-value">
                <template v-if="current.tags">
                  <span v-for="t in current.tags.split(',').filter(t => t)" :key="t"
                        class="mini-tag" :class="{ 'mini-tag--risk': isHighRiskTag(t) }" style="margin-right: 6px">{{ t }}</span>
                </template>
                <template v-else>暂无标签</template>
              </div>
            </div>
            <div class="detail-item detail-item--wide">
              <div class="detail-label">说明 Description</div>
              <div class="detail-value detail-desc">{{ current.description || '暂无说明' }}</div>
            </div>
          </div>

          <div class="detail-actions">
            <el-button type="primary" :disabled="current.status !== 'AVAILABLE'" @click="goBook(current)">
              立即预约 Book
            </el-button>
            <template v-if="store.isAdmin">
              <el-button @click="openDialog(current)">编辑 Edit</el-button>
              <el-button type="danger" plain @click="remove(current)">删除 Delete</el-button>
            </template>
          </div>

          <!-- 设备资料库 Attachments -->
          <div class="file-section">
            <div class="file-head">
              <span class="file-title">设备资料库 <span class="title-en">SOP / Documents</span></span>
              <el-upload v-if="store.isAdmin" :show-file-list="false" :before-upload="beforeUpload"
                         :http-request="doUpload" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.png,.jpg,.jpeg,.gif,.webp">
                <el-button size="small" type="primary" plain :loading="uploading">上传附件 Upload</el-button>
              </el-upload>
            </div>
            <div class="file-list" v-loading="filesLoading">
              <div v-for="f in files" :key="f.id" class="file-item">
                <span class="file-icon">{{ fileIcon(f.fileName) }}</span>
                <div class="file-info">
                  <div class="file-name">{{ f.fileName }}</div>
                  <div class="file-meta">{{ fmtSize(f.fileSize) }} · {{ f.uploaderName || '管理员' }} · {{ fmtDate(f.createTime) }}</div>
                </div>
                <div class="file-ops">
                  <el-button size="small" link type="primary" @click="previewFile(f)">预览</el-button>
                  <el-button size="small" link type="primary" @click="downloadFile(f)">下载</el-button>
                  <el-button v-if="store.isAdmin" size="small" link type="danger" @click="deleteFile(f)">删除</el-button>
                </div>
              </div>
              <el-empty v-if="!filesLoading && files.length === 0" description="暂无资料附件"
                        :image-size="60" style="padding: 12px 0" />
            </div>
          </div>
        </template>

        <el-empty v-else description="从右侧选择一台设备 Select an equipment" />
      </div>

      <!-- ============ 从属栏：设备卡片列表 ============ -->
      <div class="md-side">
        <div class="md-side-title">设备列表 <span class="title-en">List · {{ total }}</span>
          <span v-if="currentNotInPage" class="keep-hint">详情固定为已选设备</span>
        </div>

        <div class="side-filter">
          <el-input v-model="query.keyword" placeholder="搜索名称 / 型号 Search" clearable size="small"
                    @keyup.enter="search" @clear="search" />
          <el-select v-model="query.status" placeholder="状态" clearable size="small" class="status-select"
                     @change="search" @clear="search" style="width: 88px">
            <el-option v-for="(label, key) in EQUIPMENT_STATUS" :key="key" :label="label" :value="key" />
          </el-select>
        </div>
        <!-- 设备大类（单选，可点叉清除） + 使用属性（附加筛选，可与大类叠加） -->
        <div class="side-filter" style="margin-top: 6px">
          <el-select v-model="query.categoryTag" placeholder="设备大类" clearable size="small"
                     class="cat-select" @change="search" @clear="search">
            <el-option v-for="t in EQUIPMENT_CATEGORY_TAGS" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="query.usageTag" placeholder="使用属性" clearable size="small"
                     class="usage-select" @change="search" @clear="search">
            <el-option v-for="t in EQUIPMENT_USAGE_TAGS" :key="t" :label="t" :value="t" />
          </el-select>
        </div>

        <div class="card-list" v-loading="loading">
          <div v-for="item in list" :key="item.id" class="eq-card"
               :class="{ 'is-active': current && current.id === item.id }" @click="current = item">
            <div class="eq-card-top">
              <span class="eq-card-icon">
                <el-icon :size="17"><component :is="deviceIcon(item.name, item.category)" /></el-icon>
              </span>
              <span class="eq-card-name">{{ item.name }}</span>
              <el-tag :type="statusTagType(item.status)" size="small">
                {{ EQUIPMENT_STATUS[item.status] || item.status }}
              </el-tag>
            </div>
            <div class="eq-card-meta">
              <span>{{ item.model || '—' }}</span>
              <span class="eq-dot">·</span>
              <span>{{ item.location || '—' }}</span>
            </div>
            <div v-if="item.tags" class="eq-card-tags">
              <span v-for="t in item.tags.split(',').filter(t => t).slice(0, 2)" :key="t"
                    class="mini-tag" :class="{ 'mini-tag--risk': isHighRiskTag(t) }">{{ t }}</span>
            </div>
          </div>

          <el-empty v-if="!loading && list.length === 0"
                    :description="hasFilter ? '没有符合筛选条件的设备，可清除筛选后重试' : '暂无设备'" :image-size="70" />
        </div>

        <el-pagination small background layout="prev, pager, next" :total="total" :page-size="query.size"
                       v-model:current-page="query.page" @current-change="onPage" class="side-pager" />
      </div>
    </div>
  </div>

  <!-- 图片预览弹窗 -->
  <el-dialog v-model="previewVisible" title="附件预览 Preview" width="640px">
    <img :src="previewUrl" style="width: 100%; border-radius: 10px" alt="preview" />
  </el-dialog>

  <!-- 新增 / 编辑弹窗 -->
  <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备 Edit' : '新增设备 Add'" width="520px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="设备名称" required><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="型号"><el-input v-model="form.model" /></el-form-item>
      <el-form-item label="分类"><el-input v-model="form.category" placeholder="如：光谱分析" /></el-form-item>
      <el-form-item label="存放位置"><el-input v-model="form.location" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width: 100%">
          <el-option v-for="(label, key) in EQUIPMENT_STATUS" :key="key" :label="label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select v-model="form.tagList" multiple placeholder="绑定标签（可多选）" style="width: 100%">
          <el-option v-for="t in EQUIPMENT_TAGS" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消 Cancel</el-button>
      <el-button type="primary" @click="save">保存 Save</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onActivated, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { equipmentPage, equipmentCategories, equipmentAdd, equipmentUpdate, equipmentDelete, EQUIPMENT_STATUS,
         EQUIPMENT_TAGS, EQUIPMENT_CATEGORY_TAGS, EQUIPMENT_USAGE_TAGS, HIGH_RISK_TAGS,
         fileList, fileDelete, fileDownloadUrl, uploadFile, exportEquipment, saveBlob } from '../api'
import { View, Loading, Connection, TrendCharts, ScaleToOriginal, Memo, Sunny, Refrigerator,
         Grid, Refresh, Sort, HotWater, WindPower, Sunrise, Pouring, Odometer, IceDrink, Tools } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const store = useUserStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const categories = ref([])
const current = ref(null)
const query = reactive({ keyword: '', category: '', status: '', categoryTag: '', usageTag: '', page: 1, size: 10 })
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '', model: '', category: '', location: '', status: 'AVAILABLE',
                        description: '', tagList: [] })

/** 是否高危标签（安全须知 / 周额度管控） */
function isHighRiskTag(t) {
  return HIGH_RISK_TAGS.includes(t)
}

/** 分类图标：按设备主体渲染（Element Plus 线性图标） */
const ICON_RULES = [
  [['显微镜'], View],
  [['离心'], Loading],
  [['PCR', 'pcr', '基因', '扩增'], Connection],
  [['分光', '色谱', '光谱', '成像'], TrendCharts],
  [['天平'], ScaleToOriginal],
  [['pH', 'PH', 'ph', '酸度'], Memo],
  [['干燥', '灭菌', '烘箱'], Sunny],
  [['冰箱', '低温', '液氮'], Refrigerator],
  [['酶标', '微孔'], Grid],
  [['搅拌'], Refresh],
  [['摇床', '振荡'], Sort],
  [['水浴'], HotWater],
  [['超声'], WindPower],
  [['培养箱'], Sunrise],
  [['蒸发', '蒸馏'], Pouring],
  [['泵'], Odometer],
  [['制冰'], IceDrink]
]
const CATEGORY_ICON = {
  '显微成像': View, '分离纯化': Pouring, '光谱分析': TrendCharts,
  '分子生物': Connection, '常规仪器': Tools, '清洗灭菌': Sunny, '储存设备': Refrigerator
}

/**
 * 图标匹配规则：先按设备名称关键词匹配，未命中再按分类兜底。
 * 管理员新增设备时无需额外配置，自动套用同一套规则。
 */
function deviceIcon(name, category) {
  const n = name || ''
  for (const [keys, icon] of ICON_RULES) {
    if (keys.some(k => n.includes(k))) return icon
  }
  return CATEGORY_ICON[category] || Tools
}

function statusTagType(status) {
  return { AVAILABLE: 'success', UNDER_REPAIR: 'warning', SCRAPPED: 'danger' }[status] || 'info'
}

/** 当前选中设备不在本页（翻页后仍保留详情）时给出提示 */
const currentNotInPage = computed(() =>
  !!current.value && !loading.value && list.value.length > 0 &&
  !list.value.some(i => i.id === current.value.id))

/** 当前生效的标签：设备大类 + 使用属性 可叠加（后端按 AND 匹配） */
const activeTags = computed(() => [query.categoryTag, query.usageTag].filter(Boolean))
/** 是否存在任一筛选条件（用于空态文案） */
const hasFilter = computed(() =>
  !!(query.keyword || query.status || query.category || query.categoryTag || query.usageTag))

/** 查询参数：空值不下发，标签以逗号串传递（后端支持逗号分隔多标签） */
function pageParams() {
  return {
    keyword: query.keyword || undefined,
    category: query.category || undefined,
    status: query.status || undefined,
    tag: activeTags.value.length ? activeTags.value.join(',') : undefined,
    page: query.page,
    size: query.size
  }
}

/** 接收看板卡片等入口带来的筛选条件（如 status=AVAILABLE） */
function applyRouteQuery() {
  const { status, category, keyword, tag } = route.query
  query.status = status ? String(status) : ''
  query.category = category ? String(category) : ''
  query.keyword = keyword ? String(keyword) : ''
  // 路由带的 tag 支持逗号分隔，按「大类 / 使用属性」各自归位
  const tags = tag ? String(tag).split(',').map(t => t.trim()).filter(Boolean) : []
  query.categoryTag = tags.find(t => EQUIPMENT_CATEGORY_TAGS.includes(t)) || ''
  query.usageTag = tags.find(t => EQUIPMENT_USAGE_TAGS.includes(t)) || ''
  query.page = 1
}

/**
 * mode 取值：
 *   'reset' 选中本页第一台（首次进入 / 切换筛选条件后的兜底）
 *   'keep'  若原选中设备仍在结果中则保持，否则选中第一台
 *   'pager' 翻页不做任何切换，始终保持用户手动点选的那台设备
 */
async function load(mode = 'reset') {
  loading.value = true
  try {
    const pageData = await equipmentPage(pageParams())
    list.value = pageData.records || []
    total.value = Number(pageData.total || 0)
    if (mode === 'pager') {
      // 翻页不影响当前选中的设备
      if (!current.value) current.value = list.value[0] || null
    } else if (mode === 'keep') {
      if (!list.value.some(i => i.id === current.value?.id)) {
        current.value = list.value[0] || null
      }
    } else {
      current.value = list.value[0] || null
    }
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load('keep')
}

function onPage() {
  load('pager')
}

function openDialog(row) {
  const src = row || { id: null, name: '', model: '', category: '', location: '', status: 'AVAILABLE', description: '' }
  Object.assign(form, src)
  form.tagList = (src.tags ? src.tags.split(',').filter(t => t) : [])
  dialogVisible.value = true
}

async function save() {
  if (!form.name) return ElMessage.warning('请填写设备名称')
  const payload = { ...form, tags: (form.tagList || []).join(',') }
  delete payload.tagList
  if (form.id) await equipmentUpdate(payload)
  else await equipmentAdd(payload)
  dialogVisible.value = false
  ElMessage.success('保存成功')
  await load('keep')
  // 编辑的就是当前选中项时，同步刷新主栏
  if (current.value && form.id === current.value.id) {
    current.value = list.value.find(i => i.id === form.id) || current.value
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除设备「${row.name}」吗？`, '警告', { type: 'warning' })
  await equipmentDelete(row.id)
  ElMessage.success('删除成功')
  current.value = null
  load()
}

function goBook(row) {
  router.push({ path: '/calendar', query: { equipmentId: row.id } })
}

/* ---------------- 导出台账 ---------------- */
const exporting = ref(false)
async function exportExcel() {
  exporting.value = true
  try {
    const blob = await exportEquipment({
      keyword: query.keyword || undefined,
      category: query.category || undefined,
      status: query.status || undefined,
      tag: activeTags.value.length ? activeTags.value.join(',') : undefined
    })
    saveBlob(blob, `设备台账表_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

/* ---------------- 设备资料库附件 ---------------- */
const files = ref([])
const filesLoading = ref(false)
const uploading = ref(false)

async function loadFiles() {
  if (!current.value) { files.value = []; return }
  filesLoading.value = true
  try {
    files.value = await fileList(current.value.id)
  } finally {
    filesLoading.value = false
  }
}

function fileIcon(name) {
  const ext = (name || '').split('.').pop().toLowerCase()
  if (ext === 'pdf') return '📕'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) return '🖼️'
  if (['doc', 'docx'].includes(ext)) return '📘'
  if (['xls', 'xlsx'].includes(ext)) return '📗'
  return '📄'
}

const fmtSize = size => {
  if (!size) return '0 B'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}
const fmtDate = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

function beforeUpload(file) {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 20MB')
    return false
  }
  return true
}

async function doUpload({ file }) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('equipmentId', current.value.id)
    await uploadFile(fd)
    ElMessage.success('上传成功')
    loadFiles()
  } finally {
    uploading.value = false
  }
}

async function previewFile(f) {
  const blob = await fetchBlob(fileDownloadUrl(f.id, true))
  const ext = (f.fileName || '').split('.').pop().toLowerCase()
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) {
    showImage(URL.createObjectURL(blob))
  } else {
    // PDF / TXT 等在新标签页打开
    window.open(URL.createObjectURL(blob), '_blank')
  }
}

async function downloadFile(f) {
  const blob = await fetchBlob(fileDownloadUrl(f.id))
  saveBlob(blob, f.fileName)
}

async function deleteFile(f) {
  await ElMessageBox.confirm(`确定删除附件「${f.fileName}」吗？`, '警告', { type: 'warning' })
  await fileDelete(f.id)
  ElMessage.success('删除成功')
  loadFiles()
}

/** 带鉴权头拉取文件流 */
async function fetchBlob(url) {
  const token = localStorage.getItem('token')
  const resp = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!resp.ok) {
    ElMessage.error('文件获取失败')
    throw new Error('download failed')
  }
  return resp.blob()
}

/** 图片预览弹窗 */
const previewVisible = ref(false)
const previewUrl = ref('')
function showImage(url) {
  previewUrl.value = url
  previewVisible.value = true
}

onActivated(async () => {
  applyRouteQuery()
  load()
  categories.value = await equipmentCategories()
})

// 已在设备名录页时，从看板再次跳转（query 变化）同样生效
watch(() => ({ ...route.query }), () => {
  if (route.path !== '/equipment') return
  applyRouteQuery()
  load()
})

// 切换选中设备时同步刷新附件列表
watch(current, () => loadFiles())
</script>

<style scoped>
/* ============ 双栏主从布局 ============ */
.md-page {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 130px);
}

.md-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-sub {
  margin-top: 5px;
  font-size: 13px;
  color: #666666;
  letter-spacing: 0.3px;
}
.title-en {
  font-size: 12.5px;
  font-weight: 400;
  color: #777777;
  letter-spacing: 0.5px;
  margin-left: 6px;
}

.md-layout {
  display: flex;
  gap: 18px;
  flex: 1;
  min-height: 0;
}

/* ---- 主栏：详情面板 ---- */
.md-main {
  flex: 1;
  min-width: 0;
  background: #f7fafb;
  border: 1px solid #e6edf0;
  border-radius: 14px;
  padding: 22px 26px;
  display: flex;
  flex-direction: column;
}
.md-main-title,
.md-side-title {
  font-size: 14px;
  font-weight: 600;
  color: #454b4f;
  margin-bottom: 16px;
}
.md-main-title::before,
.md-side-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 12px;
  border-radius: 2px;
  background: #8fa9bc;
  margin-right: 8px;
}
.keep-hint {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e4eef4;
  color: #3d5a6b;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.2px;
  vertical-align: 1px;
}

.detail-top {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px dashed #e6edf0;
}
.detail-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e6edf0;
  box-shadow: 0 2px 8px rgba(150, 165, 175, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6f8fa5;
}
.detail-name-wrap { flex: 1; min-width: 0; }
.detail-name {
  font-size: 20px;
  font-weight: 700;
  color: #333333;
}
.detail-model {
  margin-top: 4px;
  font-size: 13px;
  color: #666666;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 18px;
}
.detail-item {
  background: #ffffff;
  border: 1px solid #ecf1f4;
  border-radius: 10px;
  padding: 12px 16px;
}
.detail-item--wide { grid-column: span 2; }
.detail-label {
  font-size: 12.5px;
  font-weight: 500;
  color: #666666;
  letter-spacing: 0.3px;
  margin-bottom: 5px;
}
.detail-value {
  font-size: 14px;
  font-weight: 500;
  color: #333333;
  line-height: 1.65;
}
.detail-desc { color: #333333; }

.detail-actions {
  margin-top: auto;
  padding-top: 20px;
  display: flex;
  gap: 4px;
}

/* ---- 设备资料库附件 ---- */
.file-section {
  margin-top: 18px;
  background: #ffffff;
  border: 1px solid #ecf1f4;
  border-radius: 10px;
  padding: 14px 16px;
}
.file-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.file-title { font-size: 14px; font-weight: 700; color: #333333; }
.file-list { display: flex; flex-direction: column; gap: 6px; max-height: 200px; overflow-y: auto; }
.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border: 1px solid #f1f5f7;
  border-radius: 8px;
  transition: border-color 0.2s, background-color 0.2s;
}
.file-item:hover { border-color: #bcd0da; background: #f7fafb; }
.file-icon { font-size: 17px; flex-shrink: 0; }
.file-info { flex: 1; min-width: 0; }
.file-name {
  font-size: 13.5px;
  font-weight: 500;
  color: #333333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-meta { font-size: 12px; color: #777777; margin-top: 3px; }
.file-ops { flex-shrink: 0; }

/* ---- 从属栏：卡片列表 ---- */
.md-side {
  width: 330px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.side-filter {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}
.side-filter .el-input { flex: 1; }
.side-filter .el-select { width: 84px; }
/* 设备大类 / 使用属性 两个筛选下拉：等分剩余宽度 */
.side-filter .cat-select { flex: 1; width: auto; }
.side-filter .usage-select { flex: 1; width: auto; }
.side-filter .cat-select :deep(.el-select__wrapper),
.side-filter .usage-select :deep(.el-select__wrapper) {
  border-radius: 8px;
  background: #fbfcfd;
  box-shadow: 0 0 0 1px #e3ebef inset;
}
.side-filter .cat-select :deep(.el-select__wrapper.is-focused),
.side-filter .usage-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px #9db8c9 inset;
}
/* 已选中筛选时：文字用莫兰迪深蓝，便于识别当前筛选状态 */
.side-filter .cat-select :deep(.el-select__selected-item:not(.is-transparent)),
.side-filter .usage-select :deep(.el-select__selected-item:not(.is-transparent)) {
  color: #3d5a6b;
  font-weight: 600;
}

.card-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 2px;
  max-height: calc(100vh - 300px);
}

.eq-card {
  background: #ffffff;
  border: 1px solid #ecf1f4;
  border-radius: 12px;
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s, background-color 0.2s;
}
.eq-card:hover {
  border-color: #bcd0da;
  box-shadow: 0 4px 14px rgba(150, 165, 175, 0.14);
  transform: translateY(-1px);
}
.eq-card.is-active {
  background: #eef5f8;
  border-color: #8fa9bc;
  box-shadow: 0 3px 12px rgba(143, 169, 188, 0.2);
}

.eq-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.eq-card-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  color: #7f9bb0;
}
.eq-card.is-active .eq-card-icon { color: #5f7f95; }
.eq-card-name {
  flex: 1;
  min-width: 0;
  font-size: 14.5px;
  font-weight: 600;
  color: #333333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.eq-card-meta {
  margin-top: 8px;
  padding-left: 26px;
  font-size: 12.5px;
  color: #666666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.eq-dot { margin: 0 5px; color: #9aa4aa; }

/* 卡片/详情小标签 */
.eq-card-tags { margin-top: 6px; padding-left: 26px; display: flex; gap: 4px; flex-wrap: wrap; }
.mini-tag {
  display: inline-block;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  padding: 4px 8px;
  border-radius: 999px;
  background: #e9f0f4;
  color: #3d5a6b;
  border: 1px solid #c9dbe5;
  white-space: nowrap;
}
.mini-tag--risk {
  background: #f4e9e9;
  color: #8a5252;
  border-color: #e5cdcd;
  font-weight: 600;
}

.side-pager {
  margin-top: 12px;
  justify-content: center;
}
</style>
