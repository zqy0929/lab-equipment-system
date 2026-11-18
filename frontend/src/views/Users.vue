<template>
  <div class="page-card">
    <div class="page-title">用户管理 <span class="title-en">Users · Roles &amp; Permissions</span></div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索用户名 / 姓名 / 学号 / 院系" clearable style="width: 220px"
                @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <span class="toolbar-hint">系统最高管理员账号（admin）受保护，不可删除、禁用、拉黑或变更角色</span>
    </div>

    <el-table :data="list" v-loading="loading">
      <el-table-column label="用户名" width="180">
        <template #default="{ row }">
          <span class="uname">{{ row.username }}</span>
          <el-tag v-if="isSuperAdmin(row)" size="small" type="warning" effect="plain" class="root-tag">最高管理员</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="realName" label="姓名" width="110" />
      <el-table-column prop="gender" label="性别" width="70" />
      <el-table-column prop="employeeNo" label="学号/工号" width="130">
        <template #default="{ row }">
          <span class="emp-no">{{ row.employeeNo || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="department" label="院系/课题组" min-width="150" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="角色" width="170">
        <template #default="{ row }">
          <span v-if="isSuperAdmin(row)" class="guard-wrap guard-wrap--locked" @click="warnProtected">
            <el-select :model-value="row.role" size="small" style="width: 130px" disabled>
              <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
          </span>
          <el-select v-else :model-value="row.role" size="small" style="width: 130px"
                     :disabled="row.id === store.user?.id" @change="val => changeRole(row, val)">
            <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="170" />
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.auditStatus === 'PENDING'" type="warning" size="small">待审核</el-tag>
          <el-tag v-else-if="row.auditStatus === 'REJECTED'" type="info" size="small">已驳回</el-tag>
          <el-tag v-else type="success" size="small">已通过</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="黑名单" width="90">
        <template #default="{ row }">
          <el-tag :type="row.blacklisted === 1 ? 'danger' : 'success'" size="small">
            {{ row.blacklisted === 1 ? '黑名单' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <template v-if="isSuperAdmin(row)">
            <!-- 最高管理员：删除 / 禁用 / 黑名单 全部置灰，仅重置密码可用 -->
            <span class="guard-wrap" @click="warnProtected">
              <el-button size="small" link class="btn-locked">加入黑名单</el-button>
            </span>
            <span class="guard-wrap" @click="warnProtected">
              <el-button size="small" link class="btn-locked">禁用</el-button>
            </span>
            <el-button size="small" link type="primary" @click="resetPwd(row)">重置密码</el-button>
            <span class="guard-wrap" @click="warnProtected">
              <el-button size="small" link class="btn-locked">删除</el-button>
            </span>
          </template>
          <template v-else>
            <!-- 待审核账号：优先展示 通过 / 驳回 -->
            <template v-if="row.auditStatus === 'PENDING'">
              <el-button size="small" link type="success" @click="auditUser(row, 'APPROVED')">通过</el-button>
              <el-button size="small" link type="danger" @click="auditUser(row, 'REJECTED')">驳回</el-button>
            </template>
            <el-button size="small" link :type="row.blacklisted === 1 ? 'success' : 'danger'" @click="toggleBlacklist(row)">
              {{ row.blacklisted === 1 ? '移出黑名单' : '加入黑名单' }}
            </el-button>
            <el-button size="small" link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" link type="primary" @click="resetPwd(row)">重置密码</el-button>
            <el-button size="small" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
  </div>
</template>

<script setup>
import { ref, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userPage, userRole, userStatus, userResetPassword, userDelete, userBlacklist, userAudit } from '../api'
import { useUserStore } from '../store/user'

const store = useUserStore()

/** 系统最高管理员账号：受保护，禁止删除 / 禁用 / 拉黑 / 改角色 */
const SUPER_ADMIN = 'admin'
const isSuperAdmin = row => String(row?.username || '').toLowerCase() === SUPER_ADMIN

/**
 * 角色字典：固定 5 项，每项唯一（学生 / 教师 / 安全员 / 设备保管员 / 管理员）
 * 历史遗留的 USER 角色统一按「学生」处理，不再单独出现在下拉中
 */
const ROLE_OPTIONS = [
  { value: 'STUDENT', label: '学生' },
  { value: 'TEACHER', label: '教师' },
  { value: 'SAFETY', label: '安全员' },
  { value: 'KEEPER', label: '设备保管员' },
  { value: 'ADMIN', label: '管理员' }
]
const ROLE_LABEL = ROLE_OPTIONS.reduce((m, r) => { m[r.value] = r.label; return m }, {})
/** 旧角色值归一化，保证下拉一定能匹配到唯一选项 */
const normalizeRole = r => (r === 'USER' ? 'STUDENT' : r)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const keyword = ref('')

/** 受保护账号操作拦截弹窗 */
function warnProtected() {
  ElMessageBox.alert('该账号为系统最高管理员，禁止执行此操作', '操作已被拦截', {
    type: 'warning',
    confirmButtonText: '我知道了'
  }).catch(() => {})
}

async function load() {
  loading.value = true
  try {
    const data = await userPage({ keyword: keyword.value, page: page.value, size })
    list.value = (data.records || []).map(u => ({ ...u, role: normalizeRole(u.role) }))
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function changeRole(row, role) {
  if (isSuperAdmin(row)) return warnProtected()
  if (!ROLE_LABEL[role]) return ElMessage.warning('角色不合法')
  try {
    await ElMessageBox.confirm(
      `确定将「${row.realName || row.username}」的角色变更为「${ROLE_LABEL[role]}」吗？`,
      '变更角色', { type: 'warning' })
  } catch {
    return load() // 取消时重新拉取，恢复下拉原值
  }
  await userRole(row.id, role)
  ElMessage.success('角色已变更')
  load()
}

async function toggleBlacklist(row) {
  if (isSuperAdmin(row)) return warnProtected()
  const target = row.blacklisted === 1 ? 0 : 1
  await ElMessageBox.confirm(
    target === 1 ? `确定将「${row.realName || row.username}」加入预约黑名单吗？加入后将无法发起任何预约。`
                 : `确定将「${row.realName || row.username}」移出黑名单吗？`,
    '黑名单管理', { type: 'warning' })
  await userBlacklist(row.id, target)
  ElMessage.success(target === 1 ? '已加入黑名单' : '已移出黑名单')
  load()
}

/** 注册审核：通过 → 所申请角色生效；驳回 → 维持默认学生权限 */
async function auditUser(row, action) {
  const name = row.realName || row.username
  const ROLE_CN = { TEACHER: '教师', SAFETY: '安全员', KEEPER: '设备保管员', ADMIN: '管理员' }
  const tip = action === 'APPROVED'
    ? `确定通过「${name}」的${ROLE_CN[row.role] || row.role}角色申请吗？通过后角色权限立即生效。`
    : `确定驳回「${name}」的角色申请吗？驳回后该账号将维持默认学生权限。`
  await ElMessageBox.confirm(tip, '注册审核', { type: 'warning' })
  await userAudit(row.id, action)
  ElMessage.success(action === 'APPROVED' ? '审核通过，角色权限已生效' : '已驳回，该账号维持学生权限')
  load()
}

async function toggleStatus(row) {
  if (isSuperAdmin(row)) return warnProtected()
  const status = row.status === 1 ? 0 : 1
  await userStatus(row.id, status)
  ElMessage.success(status === 1 ? '已启用' : '已禁用')
  load()
}

async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt(
    `为「${row.realName || row.username}」设置新密码（至少 6 位）`, '重置密码', {
      inputValidator: v => (v && v.length >= 6) || '密码长度不能少于 6 位'
    })
  await userResetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function remove(row) {
  if (isSuperAdmin(row)) return warnProtected()
  await ElMessageBox.confirm(`确定删除用户「${row.realName || row.username}」吗？该操作不可恢复。`,
    '警告', { type: 'warning' })
  await userDelete(row.id)
  ElMessage.success('删除成功')
  load()
}

onActivated(load)
</script>

<style scoped>
.title-en {
  font-size: 12.5px;
  font-weight: 400;
  color: #777777;
  letter-spacing: 0.5px;
  margin-left: 6px;
}
.toolbar-hint {
  margin-left: auto;
  font-size: 12.5px;
  color: #9a8b76;
  background: #faf5ed;
  border: 1px dashed #e6d8c4;
  border-radius: 6px;
  padding: 4px 10px;
}
.uname { color: #3f4a44; font-weight: 500; }
.emp-no { color: #6b7a74; font-family: Consolas, Menlo, monospace; font-size: 13px; letter-spacing: 0.5px; }
.root-tag { margin-left: 6px; }

/* 受保护账号的按钮：视觉置灰但保留点击以弹出拦截提示 */
.btn-locked {
  color: #c0c4cc !important;
  cursor: not-allowed;
}
.btn-locked:hover {
  color: #c0c4cc !important;
}
.guard-wrap {
  display: inline-block;
  cursor: not-allowed;
  margin-right: 6px;
}
/* 禁用的角色下拉：让点击穿透到外层，从而仍可弹出拦截提示 */
.guard-wrap--locked :deep(.el-select) {
  pointer-events: none;
  width: 130px;
}
.guard-wrap--locked :deep(.el-select__wrapper) {
  background: #f5f5f5;
}
</style>
