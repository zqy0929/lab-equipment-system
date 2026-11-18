<template>
  <div class="manual-page">
    <!-- 左侧：设备列表 -->
    <div class="page-card manual-side">
      <div class="side-head">
        <el-input v-model="keyword" placeholder="搜索设备名称 / 分类" clearable :prefix-icon="Search" />
        <el-select v-model="category" placeholder="全部分类" clearable class="cat-select">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
      </div>
      <div class="side-stat">
        共 {{ filteredList.length }} 台设备 · 已有手册 {{ manualCount }} 台
      </div>
      <div class="equip-list" v-loading="listLoading">
        <div v-for="e in filteredList" :key="e.equipmentId" class="equip-item"
             :class="{ 'is-active': current?.equipmentId === e.equipmentId }" @click="select(e.equipmentId)">
          <div class="equip-main">
            <span class="equip-name">{{ e.name }}</span>
            <span class="equip-sub">{{ e.category || '未分类' }} · {{ e.location || '位置待定' }}</span>
          </div>
          <el-tag :type="e.hasManual ? 'success' : 'info'" size="small" effect="light" class="manual-flag">
            {{ e.hasManual ? '已有手册' : '暂无' }}
          </el-tag>
        </div>
        <el-empty v-if="!listLoading && filteredList.length === 0" description="未找到设备" :image-size="60" />
      </div>
    </div>

    <!-- 右侧：面包屑 + 手册详情 -->
    <div class="manual-main-wrap">
      <el-breadcrumb separator="/" class="manual-crumb">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/manual' }">设备手册</el-breadcrumb-item>
        <el-breadcrumb-item v-if="current">{{ current.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="page-card manual-main" v-loading="detailLoading">
        <template v-if="current">
          <div class="manual-head">
            <div class="head-text">
              <div class="manual-title">{{ current.name }}</div>
              <div class="manual-meta">
                <el-tag size="small" effect="light" class="tag-cat">{{ current.category || '未分类' }}</el-tag>
                <el-tag v-if="!current.openForReserve" size="small" type="warning" effect="light">未开放预约</el-tag>
                <el-tag v-if="isPreset" size="small" effect="plain" class="tag-preset">初始预置</el-tag>
                <span class="meta-text">{{ current.model || '型号待补' }}</span>
                <span class="meta-dot">·</span>
                <span class="meta-text">{{ current.location || '位置待定' }}</span>
                <span class="meta-dot">·</span>
                <span class="meta-text" v-if="isPreset">内容由系统初始预置，尚未人工修订</span>
                <span class="meta-text" v-else-if="current.editorName">
                  最近编辑：{{ current.editorName }}（{{ fmtTime(current.updateTime) }}）
                </span>
              </div>
            </div>
            <div v-if="canEditManual" class="manual-actions">
              <el-button type="primary" size="small" @click="openEdit">编辑手册</el-button>
              <el-upload :show-file-list="false" :auto-upload="false" :on-change="onAttachmentPick"
                         accept=".pdf,.doc,.docx,.txt,.png,.jpg,.jpeg,.webp">
                <el-button size="small">{{ current.attachmentName ? '替换附件' : '上传附件' }}</el-button>
              </el-upload>
              <el-button v-if="current.attachmentName" size="small" type="danger" link @click="removeAttachment">
                删除附件
              </el-button>
            </div>
          </div>

          <!-- 操作文档 -->
          <div class="manual-section">
            <div class="section-title"><span class="sec-dot sec-blue"></span>操作文档 <span class="sec-en">Operation Guide</span></div>
            <div v-if="current.content" class="manual-text pre-wrap">{{ current.content }}</div>
            <el-empty v-else description="暂无操作文档，等待设备保管员 / 管理员补充" :image-size="56" />
          </div>

          <!-- 安全注意事项 -->
          <div class="manual-section">
            <div class="section-title"><span class="sec-dot sec-amber"></span>安全注意事项 <span class="sec-en">Safety Notes</span></div>
            <div v-if="current.safetyNotes" class="manual-text pre-wrap safety-text">{{ current.safetyNotes }}</div>
            <el-empty v-else description="暂无安全注意事项" :image-size="56" />
          </div>

          <!-- 手册附件 -->
          <div class="manual-section">
            <div class="section-title"><span class="sec-dot sec-teal"></span>手册附件 <span class="sec-en">Attachment</span></div>
            <div v-if="current.attachmentName" class="attach-row">
              <div class="attach-info">
                <span class="attach-icon">{{ isImage ? '🖼' : (current.attachmentType === 'pdf' ? '📕' : '📄') }}</span>
                <div class="attach-text">
                  <div class="attach-name">{{ current.attachmentName }}</div>
                  <div class="attach-sub">{{ (current.attachmentType || '').toUpperCase() || '文件' }}{{ canPreview ? ' · 支持在线预览' : '' }}</div>
                </div>
              </div>
              <div class="attach-ops">
                <el-button v-if="canPreview" size="small" @click="previewAttachment">在线预览</el-button>
                <el-button size="small" type="primary" @click="downloadAttachment">下载</el-button>
                <el-button v-if="canEditManual" size="small" type="danger" link @click="removeAttachment">删除</el-button>
              </div>
            </div>
            <el-empty v-else description="暂无手册附件" :image-size="56" />
          </div>

          <!-- 底部快捷操作 -->
          <div class="manual-footer">
            <div class="read-state" v-if="store.user?.role === 'STUDENT'">
              <el-tag v-if="current.hasRead" type="success" effect="light" size="small">已确认阅读</el-tag>
              <span v-if="current.hasRead" class="read-time">{{ fmtTime(current.readTime) }}</span>
              <el-button v-else size="small" plain @click="confirmReadNow">我已阅读并确认</el-button>
            </div>
            <div class="footer-right">
              <el-tooltip :disabled="current.openForReserve" placement="top" content="该设备当前未开放预约">
                <span>
                  <el-button type="primary" :disabled="!current.openForReserve" @click="goReserve">
                    预约此设备
                  </el-button>
                </span>
              </el-tooltip>
            </div>
          </div>
        </template>
        <el-empty v-else-if="!detailLoading" description="请在左侧选择设备查看手册" :image-size="90" />
      </div>
    </div>
  </div>

  <!-- 编辑手册 -->
  <el-dialog v-model="editVisible" :title="`编辑手册 · ${current?.name || ''}`" width="640px">
    <el-form label-position="top">
      <el-form-item label="操作文档（操作步骤 / 使用规范）">
        <el-input v-model="editForm.content" type="textarea" :rows="8"
                  placeholder="如：1. 开机前检查电源与线路；2. 按规程预热 10 分钟；3. 使用后登记使用记录……" />
      </el-form-item>
      <el-form-item label="安全注意事项">
        <el-input v-model="editForm.safetyNotes" type="textarea" :rows="6"
                  placeholder="如：高温操作须佩戴隔热手套，使用中不得离开现场……" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveManual">保存手册</el-button>
    </template>
  </el-dialog>

  <!-- 附件在线预览 -->
  <el-dialog v-model="previewVisible" :title="`附件预览 · ${current?.attachmentName || ''}`" width="820px" top="4vh">
    <img v-if="isImage && previewUrl" :src="previewUrl" class="preview-img" alt="preview" />
    <iframe v-else-if="previewUrl" :src="previewUrl" class="preview-frame"></iframe>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { useUserStore } from '../store/user'
import { manualList, manualDetail, manualSave, manualUploadAttachment, manualDeleteAttachment,
         manualConfirmRead, manualAttachmentUrl, saveBlob } from '../api'

const router = useRouter()
const store = useUserStore()
/** 手册编辑权限：仅系统管理员与设备保管员可见【编辑手册】【上传附件】【删除附件】；学生 / 教师 / 安全员只读 */
const canEditManual = computed(() => ['ADMIN', 'KEEPER'].includes(store.user?.role))
const keyword = ref('')
const category = ref('')
const listLoading = ref(false)
const equipmentList = ref([])
const current = ref(null)
const detailLoading = ref(false)

/** 固定筛选分类（按需求给出），其余实际存在的分类自动补充，避免设备被筛掉 */
const BASE_CATEGORIES = ['储存设备', '光谱分析', '分子生物', '分离纯化', '常规仪器']
const categoryOptions = computed(() => {
  const set = new Set(BASE_CATEGORIES)
  equipmentList.value.forEach(e => { if (e.category) set.add(e.category) })
  return [...set]
})
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return equipmentList.value.filter(e => {
    const matchCat = !category.value || e.category === category.value
    const matchKw = !kw
      || (e.name || '').toLowerCase().includes(kw)
      || (e.category || '').toLowerCase().includes(kw)
    return matchCat && matchKw
  })
})
const manualCount = computed(() => equipmentList.value.filter(e => e.hasManual).length)
const isPreset = computed(() => current.value?.editorName === '系统预置模板')

const fmtTime = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

async function loadList() {
  listLoading.value = true
  try {
    equipmentList.value = await manualList()
  } finally {
    listLoading.value = false
  }
}

async function select(equipmentId) {
  detailLoading.value = true
  try {
    current.value = await manualDetail(equipmentId)
  } catch (e) {
    ElMessage.warning(e?.message || '该设备手册暂不可查看')
  } finally {
    detailLoading.value = false
  }
}

/* ---------- 编辑 ---------- */
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ content: '', safetyNotes: '' })

function openEdit() {
  editForm.content = current.value?.content || ''
  editForm.safetyNotes = current.value?.safetyNotes || ''
  editVisible.value = true
}

async function saveManual() {
  saving.value = true
  try {
    await manualSave(current.value.equipmentId, {
      content: editForm.content || null,
      safetyNotes: editForm.safetyNotes || null
    })
    editVisible.value = false
    ElMessage.success('手册已保存')
    await Promise.all([loadList(), select(current.value.equipmentId)])
  } finally {
    saving.value = false
  }
}

/* ---------- 附件 ---------- */
const isImage = computed(() => ['png', 'jpg', 'jpeg', 'webp'].includes(current.value?.attachmentType))
const canPreview = computed(() => isImage.value || ['pdf', 'txt'].includes(current.value?.attachmentType))
const previewVisible = ref(false)
const previewUrl = ref('')

async function fetchAttachmentBlob() {
  const token = localStorage.getItem('token')
  const resp = await fetch(manualAttachmentUrl(current.value.equipmentId, true),
    { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!resp.ok) throw new Error('附件加载失败')
  return URL.createObjectURL(await resp.blob())
}

async function previewAttachment() {
  try {
    previewUrl.value = await fetchAttachmentBlob()
    previewVisible.value = true
  } catch {
    ElMessage.warning('附件预览失败，请下载后查看')
  }
}

async function downloadAttachment() {
  try {
    const token = localStorage.getItem('token')
    const resp = await fetch(manualAttachmentUrl(current.value.equipmentId), {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!resp.ok) throw new Error()
    saveBlob(await resp.blob(), current.value.attachmentName || 'manual')
  } catch {
    ElMessage.warning('附件下载失败')
  }
}

async function onAttachmentPick(file) {
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('附件不能超过 20MB')
    return
  }
  await manualUploadAttachment(current.value.equipmentId, wrapFile(file.raw))
  ElMessage.success('附件已上传')
  await Promise.all([loadList(), select(current.value.equipmentId)])
}

function wrapFile(raw) {
  const fd = new FormData()
  fd.append('file', raw)
  return fd
}

async function removeAttachment() {
  const ok = await ElMessageBox.confirm('确定删除该设备的手册附件吗？', '提示', { type: 'warning' }).catch(() => null)
  if (!ok) return
  await manualDeleteAttachment(current.value.equipmentId)
  ElMessage.success('附件已删除')
  await Promise.all([loadList(), select(current.value.equipmentId)])
}

/* ---------- 阅读确认 / 预约跳转 ---------- */
async function confirmReadNow() {
  try {
    const res = await manualConfirmRead(current.value.equipmentId)
    current.value = { ...current.value, hasRead: true, readTime: res?.readTime }
    ElMessage.success('已记录阅读确认，可以预约该设备')
  } catch (e) {
    ElMessage.error(e?.message || '记录阅读确认失败')
  }
}

function goReserve() {
  if (!current.value?.openForReserve) return
  router.push({ path: '/calendar', query: { equipmentId: current.value.equipmentId } })
}

onActivated(async () => {
  await loadList()
  if (current.value) await select(current.value.equipmentId)
})
</script>

<style scoped>
.manual-page {
  display: flex;
  gap: 16px;
  align-items: stretch;
  min-height: calc(100vh - 130px);
}
.manual-side {
  width: 330px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding-bottom: 12px;
}
.side-head { display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; }
.cat-select { width: 100%; }
.side-stat { font-size: 12px; color: #6b7378; margin: 0 2px 10px; letter-spacing: 0.2px; }
.equip-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: calc(100vh - 260px);
  padding-right: 4px;
}
.equip-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #e6edf0;
  border-radius: 10px;
  padding: 10px 12px 10px 14px;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s, box-shadow 0.2s;
  background: #ffffff;
}
.equip-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 3px;
  background: transparent;
  transition: background-color 0.2s;
}
.equip-item:hover { border-color: #c3d3cd; background: #f8faf8; }
.equip-item.is-active {
  /* 柔和莫兰迪灰绿底色，选中不刺眼 */
  background: #ecf1ed;
  border-color: #b3c6bc;
  box-shadow: 0 2px 8px rgba(120, 145, 132, 0.16);
}
.equip-item.is-active::before { background: #8ba695; }
.equip-item.is-active .equip-name { color: #2f463b; }
.equip-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.equip-name { font-size: 14px; font-weight: 600; color: #333333; }
.equip-sub { font-size: 12px; color: #737b80; }
.manual-flag { flex-shrink: 0; }

.manual-main-wrap { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px; }
.manual-crumb { font-size: 13px; padding-left: 2px; }
:deep(.manual-crumb .el-breadcrumb__inner) { color: #6f777d; font-weight: 500; }
:deep(.manual-crumb .el-breadcrumb__inner.is-link:hover) { color: #4b6b5c; }
:deep(.manual-crumb .el-breadcrumb__item:last-child .el-breadcrumb__inner) { color: #2f3a36; font-weight: 600; }
:deep(.manual-crumb .el-breadcrumb__separator) { color: #b6bdb9; }

.manual-main { flex: 1; display: flex; flex-direction: column; }
.manual-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}
.head-text { min-width: 0; }
.manual-title { font-size: 20px; font-weight: 700; color: #2b2b2b; letter-spacing: 0.4px; }
.manual-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #5f666b;
  flex-wrap: wrap;
}
.meta-text { color: #5f666b; }
.meta-dot { color: #b6bdb9; }
.tag-cat { background: #eef2f5; border-color: #dbe4ea; color: #4a6472; }
.tag-preset { background: #f3f4ef; border-color: #dde0d3; color: #6e7563; font-weight: 500; }
.manual-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

.manual-section { margin-bottom: 18px; }
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #333333;
  margin-bottom: 10px;
}
.sec-en { font-size: 12px; font-weight: 500; color: #8c9298; letter-spacing: 0.6px; }
.sec-dot { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
.sec-blue { background: #8fa9bc; box-shadow: 0 0 0 3px rgba(143, 169, 188, 0.22); }
.sec-amber { background: #c9b4a0; box-shadow: 0 0 0 3px rgba(201, 180, 160, 0.28); }
.sec-teal { background: #9fb8ae; box-shadow: 0 0 0 3px rgba(159, 184, 174, 0.25); }

.manual-text {
  border: 1px solid #e6edf0;
  border-radius: 10px;
  padding: 14px 16px;
  font-size: 14px;
  color: #333333;
  line-height: 1.85;
  background: #fbfdfe;
}
.pre-wrap { white-space: pre-wrap; word-break: break-word; }
.safety-text {
  /* 低饱和莫兰迪浅卡其，文字用深卡其保证对比度 */
  background: #f4f0e7;
  border-color: #e2dac8;
  border-left: 3px solid #c9b4a0;
  color: #574f3f;
}

.attach-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px dashed #cfdde5;
  border-radius: 10px;
  padding: 12px 14px;
  background: #f8fbfc;
  flex-wrap: wrap;
}
.attach-info { display: flex; align-items: center; gap: 10px; min-width: 0; }
.attach-icon { font-size: 20px; line-height: 1; }
.attach-text { min-width: 0; }
.attach-name { font-size: 14px; font-weight: 600; color: #3d5a6b; word-break: break-all; }
.attach-sub { font-size: 12px; color: #717a80; margin-top: 2px; }
.attach-ops { display: flex; gap: 8px; flex-shrink: 0; }

.manual-footer {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid #edf1f3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.read-state { display: flex; align-items: center; gap: 8px; }
.read-time { font-size: 12px; color: #6b7378; }
.footer-right { display: flex; gap: 10px; }

.preview-img { max-width: 100%; max-height: 72vh; display: block; margin: 0 auto; border-radius: 8px; }
.preview-frame { width: 100%; height: 72vh; border: 1px solid #e6edf0; border-radius: 8px; }
</style>
