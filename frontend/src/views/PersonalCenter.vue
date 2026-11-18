<template>
  <div class="page-card person-page" v-loading="loading">
    <div class="page-title">
      个人中心 <span class="title-en">Personal Center</span>
    </div>

    <!-- 身份卡：头像 + 基本信息 + 信用积分 -->
    <div class="hero">
      <div class="avatar">{{ avatarText }}</div>
      <div class="hero-info">
        <div class="hero-name">
          {{ user.realName || user.username || '未登录' }}
          <el-tag class="role-tag" size="small" effect="light">{{ store.roleLabel }}</el-tag>
          <el-tag v-if="user.auditStatus === 'PENDING'" size="small" type="warning" effect="light">待审核</el-tag>
          <el-tag v-if="user.blacklisted === 1" size="small" type="danger" effect="light">黑名单</el-tag>
        </div>
        <div class="hero-meta">
          <span>学号 / 工号：{{ user.employeeNo || '-' }}</span>
          <span class="dot">·</span>
          <span>登录账号：{{ user.username || '-' }}</span>
          <span class="dot">·</span>
          <span>院系：{{ user.department || '未填写' }}</span>
        </div>
      </div>
      <div class="hero-credit">
        <div class="hero-credit-num" :class="{ low: credit < 60 }">{{ credit }}</div>
        <div class="hero-credit-label">信用积分 Credit</div>
      </div>
    </div>

    <div class="grid">
      <!-- 左列：基础信息 + 修改密码 -->
      <div class="col">
        <section class="card">
          <div class="card-head"><span class="bar"></span>基础个人信息<span class="card-sub">Basic Info</span></div>
          <el-form :model="infoForm" label-width="96px" class="p-form">
            <el-form-item label="学号 / 工号">
              <el-input :model-value="user.employeeNo || '-'" disabled />
              <span class="form-tip">唯一身份标识，不可自行修改</span>
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="infoForm.realName" maxlength="20" placeholder="请填写真实姓名" />
            </el-form-item>
            <el-form-item label="性别">
              <el-select v-model="infoForm.gender" clearable placeholder="选填" style="width: 160px">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </el-form-item>
            <el-form-item label="院系 / 班级">
              <el-input v-model="infoForm.department" maxlength="40" placeholder="如：化学与化工学院 2024 级" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="infoForm.phone" maxlength="20" placeholder="用于接收实验通知（选填）" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="infoForm.email" maxlength="60" placeholder="选填" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingInfo" @click="saveInfo">保存信息</el-button>
              <el-button @click="fillInfoForm">重置</el-button>
            </el-form-item>
          </el-form>
        </section>

        <section class="card">
          <div class="card-head"><span class="bar"></span>修改密码<span class="card-sub">Change Password</span></div>
          <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-width="96px" class="p-form">
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password
                        placeholder="6-32 位，建议字母 + 数字" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password
                        placeholder="请再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingPwd" @click="savePwd">确认修改</el-button>
              <el-button @click="resetPwdForm">清空</el-button>
            </el-form-item>
          </el-form>
          <div class="card-tip">修改成功后当前登录状态保持不变，下次登录请使用新密码。</div>
        </section>
      </div>

      <!-- 右列：信用积分 + 课题组 -->
      <div class="col">
        <section class="card">
          <div class="card-head"><span class="bar"></span>我的信用积分<span class="card-sub">Credit Score</span></div>
          <div class="credit-row">
            <div class="credit-big" :class="{ low: credit < 60 }">{{ credit }}</div>
            <div class="credit-side">
              <el-progress :percentage="credit" :stroke-width="8" :show-text="false" :color="creditColor" />
              <div class="credit-state" :class="{ low: credit < 60 }">{{ creditState }}</div>
              <div class="credit-hint">信用分低于系统阈值（默认 60 分）时将无法提交预约；按时领用、归还即可保持满分。</div>
            </div>
          </div>
          <div class="sub-title">违约记录（{{ violations.length }}）</div>
          <div v-if="violations.length" class="v-list">
            <div v-for="v in violations" :key="v.id" class="v-item">
              <el-tag size="small" effect="light" :type="v.type === 'NO_SHOW' ? 'danger' : 'warning'">
                {{ v.type === 'NO_SHOW' ? '爽约' : '超时归还' }}
              </el-tag>
              <div class="v-body">
                <div class="v-remark">{{ v.remark || '未填写说明' }}</div>
                <div class="v-time">{{ fmtTime(v.createTime) }}</div>
              </div>
              <div class="v-deduct">-{{ v.deduction }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无违约记录，继续保持" :image-size="56" />
        </section>

        <!-- 近 30 天取消统计与封禁状态 -->
        <section class="card">
          <div class="card-head"><span class="bar"></span>取消预约统计<span class="card-sub">Recent 30 Days</span></div>
          <div class="cancel-stat-row">
            <div class="cancel-num" :class="{ over: cancelStats.banned }">{{ cancelStats.count30d }}</div>
            <div class="cancel-side">
              <div class="cancel-state" :class="{ over: cancelStats.banned }">
                {{ cancelStats.banned
                  ? `已超限 · 暂停预约中，剩余解禁 ${cancelStats.remainingDays} 天`
                  : `状态正常 · 阈值 ${cancelStats.limit} 次 / 30 天` }}
              </div>
              <div class="cancel-hint">
                取消计次规则：开始前 24 小时以上取消不计入；24 小时至 3 小时之间取消计 1 次；3 小时内取消计 2 次。
                30 天内累计超过阈值将暂停提交新预约（默认 7 天），管理员可手动解除。
              </div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <span class="bar"></span>课题组绑定<span class="card-sub">Research Group</span>
            <el-button v-if="isTeacher" class="head-btn" size="small" type="primary" plain @click="openCreate">
              创建课题组
            </el-button>
          </div>

          <!-- 凭邀请码加入（所有角色；教师也可加入别人的课题组） -->
          <div class="code-row">
            <el-input v-model="inviteCode" maxlength="8" placeholder="输入邀请码加入课题组" clearable
                      @keyup.enter="joinGroup" />
            <el-button type="primary" :loading="joining" @click="joinGroup">加入课题组</el-button>
          </div>

          <!-- 我负责的课题组（教师创建） -->
          <template v-if="isTeacher">
            <div class="sub-title group-sub">我负责的课题组（{{ owned.length }}）</div>
            <div v-for="g in owned" :key="'o' + g.id" class="group-box owned-box">
              <div class="group-line">
                <span class="group-name">🧪 {{ g.name }}</span>
                <span class="group-inline-meta">成员 {{ g.memberCount }} 人</span>
              </div>
              <div v-if="g.description" class="group-desc">{{ g.description }}</div>
              <div class="group-line code-line">
                <span class="invite-code">邀请码：<b>{{ g.inviteCode }}</b></span>
                <span class="group-ops">
                  <el-button size="small" text type="primary" @click="openMembers(g)">查看成员</el-button>
                  <el-button size="small" text type="primary" @click="copyCode(g.inviteCode)">复制</el-button>
                  <el-button size="small" text @click="regenCode(g)">换一个</el-button>
                </span>
              </div>
            </div>
            <el-empty v-if="!owned.length" description="尚未创建课题组，点击右上角「创建课题组」开始" :image-size="52" />
          </template>

          <!-- 我加入的课题组（别人创建的；可同时多个） -->
          <div class="sub-title group-sub">我加入的课题组（{{ joined.length }}）</div>
          <div v-for="g in joined" :key="'j' + g.id" class="group-box joined-box">
            <div class="group-line">
              <span class="group-name">👥 {{ g.name }}</span>
              <el-button size="small" text type="danger" :loading="leavingId === g.id"
                         @click="leaveGroup(g)">退出</el-button>
            </div>
            <div class="group-meta">负责人：{{ g.ownerName }} · 成员 {{ g.memberCount }} 人</div>
            <div v-if="g.description" class="group-desc">{{ g.description }}</div>
          </div>
          <el-empty v-if="!joined.length" description="暂未加入课题组，向负责人索取邀请码即可加入" :image-size="52" />

          <div class="card-tip">
            课题组为选填，不加入不影响正常预约。加入后可在预约日历看到同组成员的预约时段，负责人可看到本组全部成员的预约；可同时加入多个课题组。课题组仅共享预约信息，损耗记录与信用积分严格个人隔离。
          </div>
        </section>
      </div>
    </div>

    <!-- 创建课题组弹窗（教师）：填写名称/备注 → 生成 8 位数字邀请码 → 展示并复制 -->
    <el-dialog v-model="createVisible" title="创建课题组"
               width="480px" class="group-dlg" :close-on-click-modal="false">
      <el-form v-if="!createdGroup" label-width="96px" label-position="right"
               class="create-form" @submit.prevent>
        <el-form-item label="课题组名称" required>
          <el-input v-model="createName" maxlength="50" class="dlg-input"
                    placeholder="如：天然产物化学课题组" />
        </el-form-item>
        <el-form-item label="简单备注" class="remark-item">
          <el-input v-model="createRemark" type="textarea" :rows="3" maxlength="100" resize="none"
                    class="dlg-textarea" placeholder="选填，研究方向、组内说明等" />
        </el-form-item>
      </el-form>
      <div v-else class="created-box">
        <div class="created-name">🧪 「{{ createdGroup.name }}」创建成功，您是本组负责人</div>
        <div class="created-tip">把下面的邀请码分享给本组学生 / 教师，凭码即可加入：</div>
        <div class="created-code">{{ createdGroup.inviteCode }}</div>
        <div class="created-actions">
          <el-button type="primary" @click="copyCode(createdGroup.inviteCode)">复制邀请码</el-button>
          <el-button @click="finishCreate">完成</el-button>
        </div>
      </div>
      <template #footer>
        <div v-if="!createdGroup" class="dlg-footer">
          <el-button class="btn-cancel" @click="createVisible = false">取消</el-button>
          <el-button type="primary" class="btn-submit" :loading="creating" @click="submitCreate">
            创建并生成邀请码
          </el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 课题组成员管理弹窗（仅负责人可打开）：查看组员 + 移出 -->
    <el-dialog v-model="memberVisible" title="课题组成员管理"
               width="600px" class="member-dlg" :close-on-click-modal="false">
      <div class="member-head">
        <span class="member-group">🧪 {{ memberGroup?.name }}</span>
        <span class="member-count">共 {{ members.length }} 位组员</span>
      </div>
      <el-table v-if="members.length" v-loading="memberLoading" :data="members" size="small" class="member-table">
        <el-table-column label="姓名" min-width="130">
          <template #default="{ row }">
            <div class="m-name">
              <span class="m-avatar">{{ (row.realName || row.username || '?').slice(0, 1) }}</span>
              <span class="m-real">{{ row.realName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学号 / 工号" min-width="130">
          <template #default="{ row }">{{ row.employeeNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="108">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="roleTagType(row.role)">
              {{ ROLE_LABEL[row.role] || '学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="86" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="danger" :loading="removingId === row.id"
                       @click="removeMember(row)">移出</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else v-loading="memberLoading" description="本组暂无组员，把邀请码分享给成员即可加入" :image-size="56" />
      <div class="member-tip">
        仅课题组负责人可管理成员。移出后该成员将立即失去本组预约记录的查看权限，不影响其账号、其他课题组与设备预约等原有功能。
      </div>
      <template #footer>
        <div class="dlg-footer">
          <el-button class="btn-cancel" @click="memberVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { useUserStore } from '../store/user'
import { getMe, updateProfile, changePassword, myCredit, myViolations, reservationCancelStats,
         groupOwned, groupMine, groupJoin, groupLeave, groupCreate, groupRegenerateCode,
         groupMembers, groupRemoveMember } from '../api'

const store = useUserStore()
const loading = ref(false)
const user = computed(() => store.user || {})
const isTeacher = computed(() => user.value.role === 'TEACHER')

const credit = ref(100)
const violations = ref([])
const cancelStats = ref({ count30d: 0, limit: 5, banned: false, remainingDays: 0 })
const owned = ref([])      // 我负责的课题组（教师创建的）
const joined = ref([])     // 我加入的课题组（别人创建的，可同时多个）
const inviteCode = ref('')
const joining = ref(false)
const leavingId = ref(null)
const savingInfo = ref(false)
const savingPwd = ref(false)

/* 创建课题组弹窗 */
const createVisible = ref(false)
const createName = ref('')
const createRemark = ref('')
const creating = ref(false)
const createdGroup = ref(null)

/* 课题组成员管理弹窗（仅负责人） */
const memberVisible = ref(false)
const memberGroup = ref(null)
const members = ref([])
const memberLoading = ref(false)
const removingId = ref(null)
const ROLE_LABEL = {
  ADMIN: '管理员', TEACHER: '教师', SAFETY: '安全员',
  KEEPER: '设备保管员', STUDENT: '学生', USER: '学生'
}
const roleTagType = r => ({ TEACHER: 'success', ADMIN: 'danger', KEEPER: 'warning', SAFETY: 'info' }[r] || '')

const avatarText = computed(() => {
  const name = (user.value.realName || user.value.username || '?').trim()
  return name ? name.slice(0, 1).toUpperCase() : '?'
})
const creditState = computed(() => {
  if (credit.value >= 90) return '信用良好'
  if (credit.value >= 60) return '信用正常'
  return '预约受限'
})
const creditColor = computed(() => (credit.value < 60 ? '#c08f8f' : credit.value < 90 ? '#c9ac86' : '#8aa79a'))

const infoForm = reactive({ realName: '', gender: '', department: '', phone: '', email: '' })

const pwdRef = ref()
const pwdForm = reactive({ newPassword: '', confirmPassword: '' })
const pwdRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6-32 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const fmtTime = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

function fillInfoForm() {
  Object.assign(infoForm, {
    realName: user.value.realName || '',
    gender: user.value.gender || '',
    department: user.value.department || '',
    phone: user.value.phone || '',
    email: user.value.email || ''
  })
}

async function loadAll() {
  loading.value = true
  try {
    const [me, score, vios, ownedList, joinedList] = await Promise.all([
      getMe(), myCredit(), myViolations(), groupOwned(), groupMine()
    ])
    if (me) store.setUser(me)
    credit.value = Number(score ?? 100)
    violations.value = Array.isArray(vios) ? vios : []
    // 近 30 天取消统计（失败不阻断页面）
    reservationCancelStats().then(s => { cancelStats.value = s }).catch(() => {})
    owned.value = Array.isArray(ownedList) ? ownedList : []
    joined.value = Array.isArray(joinedList) ? joinedList : []
    fillInfoForm()
  } finally {
    loading.value = false
  }
}

async function saveInfo() {
  if (!infoForm.realName || !infoForm.realName.trim()) return ElMessage.warning('请填写姓名')
  if (infoForm.phone && !/^[\d\-+\s]{6,20}$/.test(infoForm.phone)) {
    return ElMessage.warning('手机号格式不正确')
  }
  savingInfo.value = true
  try {
    await updateProfile({
      realName: infoForm.realName.trim(),
      gender: infoForm.gender || undefined,
      department: infoForm.department,
      phone: infoForm.phone,
      email: infoForm.email
    })
    const me = await getMe()
    if (me) store.setUser(me)
    fillInfoForm()
    ElMessage.success('个人信息已保存')
  } finally {
    savingInfo.value = false
  }
}

function resetPwdForm() {
  pwdRef.value?.resetFields()
}

async function savePwd() {
  // validate() 校验失败时会 reject 一个字段对象，需捕获避免未处理异常
  const valid = await pwdRef.value.validate().catch(() => false)
  if (!valid) return
  savingPwd.value = true
  try {
    await changePassword({ newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请牢记新密码')
    resetPwdForm()
  } finally {
    savingPwd.value = false
  }
}

/* ---------- 课题组：创建 / 加入 / 退出 / 邀请码 ---------- */
function openCreate() {
  createName.value = ''
  createRemark.value = ''
  createdGroup.value = null
  createVisible.value = true
}

async function submitCreate() {
  const name = (createName.value || '').trim()
  if (!name) return ElMessage.warning('请填写课题组名称')
  creating.value = true
  try {
    const g = await groupCreate({ name, description: (createRemark.value || '').trim() })
    createdGroup.value = g
    owned.value = await groupOwned()
    ElMessage.success('课题组创建成功')
  } finally {
    creating.value = false
  }
}

function finishCreate() {
  createVisible.value = false
  createdGroup.value = null
}

async function copyCode(code) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('邀请码已复制，快分享给本组成员吧')
  } catch {
    ElMessage.info(`邀请码：${code}（请手动复制）`)
  }
}

async function regenCode(g) {
  const code = await groupRegenerateCode(g.id)
  g.inviteCode = code
  ElMessage.success(`新邀请码：${code}（旧邀请码已失效）`)
}

async function joinGroup() {
  const code = (inviteCode.value || '').trim()
  if (!code) return ElMessage.warning('请输入邀请码')
  joining.value = true
  try {
    const g = await groupJoin(code)
    inviteCode.value = ''
    joined.value = await groupMine()
    ElMessage.success(`已加入课题组「${g.name}」`)
  } finally {
    joining.value = false
  }
}

async function leaveGroup(g) {
  await ElMessageBox.confirm(
    `退出「${g.name}」后将不再共享该组成员的预约信息，确定退出吗？`,
    '退出课题组', { type: 'warning', confirmButtonText: '确定退出', cancelButtonText: '再想想' }
  )
  leavingId.value = g.id
  try {
    await groupLeave(g.id)
    joined.value = await groupMine()
    ElMessage.success('已退出课题组')
  } finally {
    leavingId.value = null
  }
}

onActivated(loadAll)

/* ---------- 成员管理（仅本组负责人可见可用） ---------- */
async function openMembers(g) {
  memberGroup.value = g
  members.value = []
  memberVisible.value = true
  await loadMembers(g.id)
}

async function loadMembers(groupId) {
  memberLoading.value = true
  try {
    const list = await groupMembers(groupId)
    members.value = Array.isArray(list) ? list : []
  } finally {
    memberLoading.value = false
  }
}

async function removeMember(row) {
  const ok = await ElMessageBox.confirm(
    '确定将该成员移出课题组？移出后该成员将无法再查看本组预约记录',
    '移出成员',
    { type: 'warning', confirmButtonText: '确定移出', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!ok) return
  removingId.value = row.id
  try {
    await groupRemoveMember(memberGroup.value.id, row.id)
    members.value = members.value.filter(m => m.id !== row.id)
    owned.value = await groupOwned()
    const fresh = owned.value.find(x => x.id === memberGroup.value?.id)
    if (fresh) memberGroup.value = fresh
    ElMessage.success(`已将「${row.realName}」移出课题组`)
  } finally {
    removingId.value = null
  }
}
</script>

<style scoped>
.person-page { padding-bottom: 22px; }

/* ---------- 身份卡 ---------- */
.hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px 22px;
  border-radius: 14px;
  background: linear-gradient(135deg, #eef2f5, #e2e9ee);
  border: 1px solid #dde6eb;
  margin-bottom: 18px;
}
.avatar {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #adb9cd, #8798ad);
  box-shadow: 0 4px 12px rgba(130, 148, 170, 0.32);
  flex-shrink: 0;
}
.hero-info { flex: 1; min-width: 0; }
.hero-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 19px;
  font-weight: 700;
  color: #2b353c;
  letter-spacing: 0.4px;
}
.role-tag { background: #e4ecf1; border-color: #cfdfe8; color: #3d5462; }
.hero-meta {
  margin-top: 8px;
  font-size: 13px;
  color: #4f5b63;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.hero-meta .dot { color: #9aa6ae; }
.hero-credit {
  text-align: center;
  padding-left: 20px;
  border-left: 1px dashed #cfdbe2;
  flex-shrink: 0;
}
.hero-credit-num { font-size: 30px; font-weight: 800; color: #5d7d8e; line-height: 1.1; }
.hero-credit-num.low { color: #b07c7c; }
.hero-credit-label { font-size: 12px; color: #5b666e; letter-spacing: 0.6px; margin-top: 3px; }

/* ---------- 两列布局 ---------- */
.grid { display: grid; grid-template-columns: 1.08fr 1fr; gap: 18px; align-items: start; }
.col { display: flex; flex-direction: column; gap: 18px; }

.card {
  background: #ffffff;
  border: 1px solid #e8eef2;
  border-radius: 14px;
  padding: 18px 20px 16px;
  box-shadow: 0 2px 8px rgba(150, 165, 175, 0.07);
}
.card-head {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 15.5px;
  font-weight: 700;
  color: #2b353c;
  margin-bottom: 16px;
}
.card-head .bar {
  width: 4px;
  height: 16px;
  border-radius: 3px;
  background: linear-gradient(180deg, #a9bcc9, #7d95a5);
}
.card-sub { font-size: 12px; font-weight: 500; color: #7d8890; letter-spacing: 0.6px; }
.card-tip { font-size: 12.5px; color: #5b666e; line-height: 1.7; margin-top: 8px; }

.p-form :deep(.el-form-item) { margin-bottom: 16px; }
.form-tip { font-size: 12.5px; color: #6b767e; margin-left: 10px; }

/* ---------- 信用 ---------- */
.credit-row { display: flex; align-items: center; gap: 18px; }
.credit-big {
  font-size: 40px;
  font-weight: 800;
  color: #5d7d8e;
  line-height: 1;
  min-width: 78px;
  text-align: center;
}
.credit-big.low { color: #b07c7c; }
.credit-side { flex: 1; min-width: 0; }
.credit-state { font-size: 13.5px; font-weight: 600; color: #4a6a5c; margin-top: 8px; }
/* 取消预约统计 */
.cancel-stat-row { display: flex; gap: 16px; align-items: flex-start; }
.cancel-num { font-size: 34px; font-weight: 700; color: #4a6c80; min-width: 56px; text-align: center; line-height: 1.2; }
.cancel-num.over { color: #a06a6a; }
.cancel-side { flex: 1; min-width: 0; }
.cancel-state { font-size: 13.5px; font-weight: 600; color: #4a6a5c; }
.cancel-state.over { color: #a06a6a; }
.cancel-hint { font-size: 12.5px; color: #5b666e; line-height: 1.65; margin-top: 6px; }
.credit-state.low { color: #a86a6a; }
.credit-hint { font-size: 12.5px; color: #5b666e; line-height: 1.65; margin-top: 4px; }
.sub-title { font-size: 13.5px; font-weight: 600; color: #38434a; margin: 18px 0 10px; }
.v-list { display: flex; flex-direction: column; gap: 8px; max-height: 220px; overflow-y: auto; }
.v-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 10px;
  background: #faf7f5;
  border: 1px solid #f0e8e4;
}
.v-body { flex: 1; min-width: 0; }
.v-remark { font-size: 13px; color: #3a454c; line-height: 1.55; word-break: break-all; }
.v-time { font-size: 12px; color: #6b767e; margin-top: 3px; }
.v-deduct { font-size: 15px; font-weight: 700; color: #b07c7c; }

/* ---------- 课题组 ---------- */
.head-btn { margin-left: auto; }
.group-sub { margin-top: 16px; }
.group-box {
  padding: 11px 14px;
  border-radius: 10px;
  margin-bottom: 8px;
  border: 1px solid #e0ebe4;
  background: #f3f7f5;
}
.group-box.owned-box { border-color: #dfe7ef; background: #f2f6fa; }
.group-box.joined-box { border-color: #e0ebe4; background: #f3f7f5; }
.group-line { display: flex; align-items: center; gap: 10px; }
.group-name { font-size: 14.5px; font-weight: 700; color: #2f4a3d; }
.owned-box .group-name { color: #3d5462; }
.group-line .el-button { margin-left: auto; }
.group-inline-meta { font-size: 12.5px; color: #5c6f64; }
.group-meta { font-size: 12.5px; color: #4f6157; margin-top: 5px; }
.group-desc { font-size: 12.5px; color: #5b6b62; margin-top: 6px; line-height: 1.6; }
.code-line { margin-top: 8px; }
.invite-code { font-size: 13px; color: #5c7484; }
.invite-code b {
  color: #3d7a5c;
  letter-spacing: 2px;
  font-family: Consolas, monospace;
  font-size: 14.5px;
}
.group-ops { margin-left: auto; display: flex; align-items: center; }
.code-row { display: flex; gap: 10px; }
.code-row .el-input { flex: 1; }

/* 创建课题组弹窗 */
.created-box { text-align: center; padding: 6px 4px 2px; }
.created-name { font-size: 15px; font-weight: 700; color: #2f4a3d; }
.created-tip { font-size: 13px; color: #5b666e; margin-top: 10px; }
.created-code {
  margin: 14px auto 4px;
  width: 240px;
  padding: 14px 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #e7efe9, #d5e4db);
  border: 1px solid #cfe0d6;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 8px;
  color: #3d6a52;
  font-family: Consolas, monospace;
}
.created-actions { margin-top: 14px; display: flex; justify-content: center; gap: 10px; }

@media (max-width: 1180px) {
  .grid { grid-template-columns: 1fr; }
}
</style>

<style>
/* ============ 创建课题组弹窗（teleport 到 body，故非 scoped；.group-dlg 前缀隔离） ============ */
.group-dlg .el-dialog {
  border-radius: 16px;
  background: #ffffff;
}
/* 标题：左上角、加粗清晰 */
.group-dlg .el-dialog__header {
  padding: 20px 24px 14px;
  margin-right: 0;
  border-bottom: 1px solid #eef3f5;
}
.group-dlg .el-dialog__title {
  font-size: 16.5px;
  font-weight: 800;
  color: #2b353c;
  letter-spacing: 0.4px;
}
.group-dlg .el-dialog__body {
  padding: 20px 24px 6px;
  background: #ffffff;
}
.group-dlg .el-dialog__footer {
  padding: 12px 24px 20px;
}

/* 标准两列表单：左固定宽标签右对齐，右输入框占满剩余宽度 */
.group-dlg .create-form .el-form-item {
  margin-bottom: 20px;
}
.group-dlg .create-form .el-form-item__label {
  width: 96px;
  padding-right: 16px;
  justify-content: flex-end;
  text-align: right;
  font-size: 13.5px;
  font-weight: 600;
  color: #3c4750;
  line-height: 40px;
}
.group-dlg .create-form .el-form-item__content {
  min-width: 0;
  line-height: normal;
}
/* 单行输入框：40px 高、内边距统一、莫兰迪浅灰底 */
.group-dlg .dlg-input .el-input__wrapper {
  min-height: 40px;
  padding: 0 13px;
  border-radius: 9px;
  background: #fbfcfd;
  box-shadow: 0 0 0 1px #e3ebef inset;
  transition: box-shadow 0.2s, background-color 0.2s;
}
.group-dlg .dlg-input .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #cfdde5 inset;
}
.group-dlg .dlg-input .el-input__wrapper.is-focus {
  background: #ffffff;
  box-shadow: 0 0 0 1px #9db8c9 inset, 0 0 0 3px rgba(157, 184, 201, 0.16);
}
.group-dlg .dlg-input .el-input__inner {
  height: 38px;
  font-size: 14px;
}
/* 多行文本框 */
.group-dlg .dlg-textarea .el-textarea__inner {
  min-height: 88px !important;
  padding: 10px 13px;
  border-radius: 9px;
  background: #fbfcfd;
  box-shadow: 0 0 0 1px #e3ebef inset;
  font-size: 14px;
  line-height: 1.65;
  transition: box-shadow 0.2s, background-color 0.2s;
}
.group-dlg .dlg-textarea .el-textarea__inner:focus {
  background: #ffffff;
  box-shadow: 0 0 0 1px #9db8c9 inset, 0 0 0 3px rgba(157, 184, 201, 0.16);
}
/* 多行文本标签与首行文字垂直对齐 */
.group-dlg .remark-item .el-form-item__label {
  line-height: 22px;
  padding-top: 9px;
}
/* 底部按钮区：右对齐，取消在左、主按钮在右 */
.group-dlg .dlg-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
.group-dlg .dlg-footer .btn-cancel {
  width: 96px;
}
.group-dlg .dlg-footer .btn-submit {
  min-width: 148px;
  letter-spacing: 0.4px;
}

/* ============ 课题组成员管理弹窗（非 scoped；.member-dlg 前缀隔离） ============ */
.member-dlg .el-dialog {
  border-radius: 16px;
  background: #ffffff;
}
.member-dlg .el-dialog__header {
  padding: 20px 24px 14px;
  margin-right: 0;
  border-bottom: 1px solid #eef3f5;
}
.member-dlg .el-dialog__title {
  font-size: 16.5px;
  font-weight: 800;
  color: #2b353c;
  letter-spacing: 0.4px;
}
.member-dlg .el-dialog__body { padding: 18px 24px 6px; background: #ffffff; }
.member-dlg .el-dialog__footer { padding: 12px 24px 20px; }
.member-dlg .dlg-footer { display: flex; justify-content: flex-end; align-items: center; }
.member-dlg .dlg-footer .btn-cancel { width: 96px; }

.member-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 14px;
  margin-bottom: 12px;
  border-radius: 10px;
  background: #f2f6fa;
  border: 1px solid #dfe7ef;
}
.member-group { font-size: 14px; font-weight: 700; color: #3d5462; }
.member-count { font-size: 12.5px; color: #5c7484; }

.member-dlg .el-table { border-radius: 10px; }
.member-dlg .el-table th.el-table__cell {
  background: #f4f7f9;
  color: #46535b;
  font-weight: 700;
  font-size: 13px;
}
.member-dlg .el-table td.el-table__cell { font-size: 13.5px; color: #3d4750; }
.member-dlg .el-table__body tr:hover > td.el-table__cell { background: #f8fafb; }
.member-dlg .el-tag { border-radius: 6px; }

.m-name { display: flex; align-items: center; gap: 9px; }
.m-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #adb9cd, #8798ad);
  flex-shrink: 0;
}
.m-real { font-weight: 600; color: #33414a; }
.member-tip { font-size: 12.5px; color: #5b666e; line-height: 1.7; margin-top: 12px; }
</style>
