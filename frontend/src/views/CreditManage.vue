<template>
  <div class="page-card">
    <div class="page-title">信用管理 <span class="title-en">Credit Management</span></div>

    <div class="tab-bar">
      <button class="tab-btn" :class="{ 'is-active': activeTab === 'users' }" @click="activeTab = 'users'">用户信用</button>
      <button class="tab-btn" :class="{ 'is-active': activeTab === 'violations' }" @click="activeTab = 'violations'">违约记录</button>
    </div>

    <!-- 用户信用列表 -->
    <div v-if="activeTab === 'users'">
      <div class="toolbar">
        <el-input v-model="userQuery.keyword" placeholder="搜索用户名 / 姓名" clearable style="width: 220px" />
        <el-button type="primary" @click="loadUsers">查询</el-button>
        <div style="flex: 1"></div>
        <span class="hint">信用低于 {{ threshold }} 分不可预约</span>
        <el-button type="primary" plain @click="openViolation()">登记违约</el-button>
      </div>

      <div class="table-wrap" :class="{ 'is-loading': usersLoading }">
        <el-table :data="users" empty-text="暂无数据">
          <el-table-column prop="username" label="用户名" width="150" />
          <el-table-column prop="realName" label="姓名" width="130" />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="{ ADMIN: 'primary', TEACHER: 'warning' }[row.role] || 'info'">
                {{ { ADMIN: '管理员', TEACHER: '老师', STUDENT: '学生' }[row.role] || row.role }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="信用积分" min-width="190">
            <template #default="{ row }">
              <div class="credit-cell">
                <span class="credit-num" :class="{ 'credit-low': scoreOf(row) < threshold }">{{ scoreOf(row) }}</span>
                <span class="credit-bar-bg">
                  <span class="credit-bar" :class="{ 'credit-bar-low': scoreOf(row) < threshold }"
                        :style="{ width: Math.min(100, Math.max(0, scoreOf(row))) + '%' }"></span>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="danger" plain @click="openViolation(row)">登记违约</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="usersLoading" class="table-mask"><span>加载中…</span></div>
      </div>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="userTotal" :page-size="userQuery.size" v-model:current-page="userQuery.page"
                     @current-change="loadUsers" />
    </div>

    <!-- 违约历史 -->
    <div v-else>
      <div class="toolbar">
        <el-select v-model="vQuery.type" placeholder="全部类型" clearable style="width: 160px" @change="loadViolations">
          <el-option label="预约爽约" value="NO_SHOW" />
          <el-option label="超时归还" value="OVERDUE" />
        </el-select>
        <el-button type="primary" @click="loadViolations">查询</el-button>
      </div>

      <div class="table-wrap" :class="{ 'is-loading': vLoading }">
        <el-table :data="violations" empty-text="暂无违约记录">
          <el-table-column prop="id" label="编号" width="80" />
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="realName" label="姓名" width="120" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="danger">{{ typeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="扣分" width="90">
            <template #default="{ row }"><span class="minus">-{{ row.deduction }}</span></template>
          </el-table-column>
          <el-table-column prop="remark" label="违约备注" min-width="200" show-overflow-tooltip />
          <el-table-column prop="operatorName" label="登记人" width="110" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <div v-if="vLoading" class="table-mask"><span>加载中…</span></div>
      </div>
      <el-pagination class="pager" background layout="total, prev, pager, next"
                     :total="vTotal" :page-size="vQuery.size" v-model:current-page="vQuery.page"
                     @current-change="loadViolations" />
    </div>

    <!-- 登记违约弹窗 -->
    <el-dialog v-model="dlgVisible" title="登记违约" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="违约用户" required>
          <el-select v-model="form.userId" filterable placeholder="选择用户" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id"
                       :label="`${u.realName || u.username}（${u.username}）· ${u.creditScore ?? 100} 分`"
                       :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="违约类型" required>
          <el-radio-group v-model="form.type">
            <el-radio-button value="NO_SHOW">预约爽约</el-radio-button>
            <el-radio-button value="OVERDUE">超时归还</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="扣减分数" required>
          <el-input-number v-model="form.deduction" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="违约备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="违约情况说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">登记并扣分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { creditUsers, violationPage, violationCreate, configGet } from '../api'

const activeTab = ref('users')
const threshold = ref(60)
const fmtTime = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')
const scoreOf = row => (row.creditScore ?? 100)

/* 用户信用列表 */
const users = ref([])
const userTotal = ref(0)
const usersLoading = ref(false)
const userQuery = reactive({ keyword: '', page: 1, size: 10 })

async function loadUsers() {
  usersLoading.value = true
  try {
    const data = await creditUsers(userQuery)
    users.value = data.records || []
    userTotal.value = Number(data.total || 0)
  } catch {
    users.value = []
    userTotal.value = 0
  } finally {
    usersLoading.value = false
  }
}

/* 违约记录 */
const violations = ref([])
const vTotal = ref(0)
const vLoading = ref(false)
const vQuery = reactive({ type: '', page: 1, size: 10 })

async function loadViolations() {
  vLoading.value = true
  try {
    const data = await violationPage(vQuery)
    violations.value = data.records || []
    vTotal.value = Number(data.total || 0)
  } catch {
    violations.value = []
    vTotal.value = 0
  } finally {
    vLoading.value = false
  }
}

function typeLabel(t) {
  return { NO_SHOW: '预约爽约', OVERDUE: '超时归还' }[t] || t
}

/* 登记违约 */
const dlgVisible = ref(false)
const saving = ref(false)
const userOptions = ref([])
const form = reactive({ userId: null, type: 'NO_SHOW', deduction: 10, remark: '' })

function openViolation(row) {
  form.userId = row ? row.id : null
  form.type = 'NO_SHOW'
  form.deduction = 10
  form.remark = ''
  creditUsers({ page: 1, size: 200 }).then(data => { userOptions.value = data.records || [] }).catch(() => {})
  dlgVisible.value = true
}

async function submit() {
  if (!form.userId) return ElMessage.warning('请选择违约用户')
  saving.value = true
  try {
    await violationCreate({ ...form, reservationId: null })
    ElMessage.success('违约登记成功，已扣减信用分')
    dlgVisible.value = false
    loadUsers()
    loadViolations()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '登记失败')
  } finally {
    saving.value = false
  }
}

onActivated(async () => {
  loadUsers()
  loadViolations()
  try {
    const config = await configGet()
    if (config.credit_threshold !== undefined) threshold.value = Number(config.credit_threshold)
  } catch { /* 使用默认阈值 */ }
})
</script>

<style scoped>
.title-en {
  font-size: 12.5px;
  font-weight: 400;
  color: #777777;
  letter-spacing: 0.5px;
  margin-left: 6px;
}
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
.tab-btn.is-active {
  background: #dee9ef;
  color: #3d5a6b;
  font-weight: 700;
}
.hint {
  font-size: 12.5px;
  color: #666666;
  margin-right: 12px;
}
.table-wrap { position: relative; min-height: 80px; }
.table-wrap.is-loading { pointer-events: none; }
.table-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(247, 250, 251, 0.72);
  border-radius: 10px;
  font-size: 13px;
  color: #666666;
  z-index: 5;
}
.pager { margin-top: 14px; justify-content: flex-end; }
.credit-cell { display: flex; align-items: center; gap: 10px; }
.credit-bar-bg {
  flex: 1;
  height: 10px;
  border-radius: 6px;
  background: #edf2f4;
  overflow: hidden;
}
.credit-bar {
  display: block;
  height: 100%;
  border-radius: 6px;
  background: linear-gradient(90deg, #a9c0cf, #8fa9bc);
  transition: width 0.35s ease;
}
.credit-bar-low { background: linear-gradient(90deg, #cdacac, #bc9494); }
.credit-num { font-weight: 700; color: #4a6c80; width: 38px; text-align: right; font-size: 14px; }
.credit-num.credit-low { color: #bc9494; }
.minus { color: #bc9494; font-weight: 600; }
</style>
