<template>
  <div class="page-card group-card">
    <!-- ============ 教师（唯一可创建 / 管理组员的角色）：创建 + 名下组管理 + 加入别人的组 ============ -->
    <template v-if="store.isTeacher">
      <div class="page-title">🧪 课题组管理 <span class="gc-sub">创建课题组、生成邀请码、管理组员；也可凭邀请码加入其他课题组</span></div>

      <div class="gc-create">
        <el-input v-model="newName" placeholder="课题组名称，如：天然产物化学课题组" maxlength="50"
                  style="width: 320px" @keyup.enter="createGroup" />
        <el-button type="primary" :loading="creating" @click="createGroup">创建课题组</el-button>
        <span class="gc-tip">课题组为选填功能；不影响个人独立使用</span>
      </div>

      <div v-for="g in groups" :key="g.id" class="gc-item">
        <div class="gc-head" @click="toggleMembers(g)">
          <span class="gc-name">{{ g.name }}</span>
          <el-tag size="small" effect="plain" type="info">{{ g.memberCount }} 名成员</el-tag>
          <span class="gc-code">邀请码：<b>{{ g.inviteCode }}</b></span>
          <span class="gc-actions" @click.stop>
            <el-button size="small" text type="primary" @click="copyCode(g)">复制邀请码</el-button>
            <el-button size="small" text @click="regenCode(g)">换一个</el-button>
          </span>
          <span class="gc-arrow" :class="{ open: openedId === g.id }">›</span>
        </div>
        <div v-if="openedId === g.id" class="gc-members" v-loading="memberLoading">
          <el-empty v-if="!members.length" description="暂无成员，把邀请码发给学生吧" :image-size="60" />
          <div v-for="m in members" :key="m.id" class="gc-member">
            <span class="gc-member-name">{{ m.realName || m.username }}</span>
            <span class="gc-member-no">{{ m.employeeNo || m.username }}</span>
            <el-tag size="small" effect="light" :type="roleTagType(m.role)">{{ ROLE_LABEL[m.role] || '学生' }}</el-tag>
            <el-button size="small" text type="danger" @click="removeMember(g, m)">移出</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!groups.length" description="还没有课题组，创建一个吧" :image-size="70" />

      <!-- 教师：凭邀请码加入其他教师的课题组（可同时多个） -->
      <div class="gc-join-title">加入其他课题组 <span class="gc-join-sub">向其他课题组负责人索取邀请码</span></div>
      <div class="gc-create">
        <el-input v-model="inviteCode" placeholder="输入课题组邀请码" maxlength="8"
                  style="width: 240px" @keyup.enter="joinGroup" />
        <el-button type="primary" plain :loading="joining" @click="joinGroup">加入课题组</el-button>
      </div>
      <div v-for="g in myJoined" :key="'j' + g.id" class="gc-joined">
        <div class="gc-joined-info">
          <span class="gc-joined-name">📌 {{ g.name }}</span>
          <span class="gc-meta">负责人：{{ g.ownerName }} · 成员 {{ g.memberCount }} 人</span>
        </div>
        <el-button size="small" type="danger" plain :loading="leavingId === g.id" @click="leaveGroup(g)">退出</el-button>
      </div>
      <el-empty v-if="!myJoined.length" description="暂未加入其他课题组" :image-size="52" />
    </template>

    <!-- ============ 学生 / 保管员 / 安全员 / 管理员：仅凭邀请码加入（不能创建） ============ -->
    <template v-else>
      <div class="page-title">🧪 我的课题组 <span class="gc-sub">选填：可同时加入多个课题组，与同组成员共享预约信息（损耗与信用不共享）</span></div>

      <div class="gc-create">
        <el-input v-model="inviteCode" placeholder="输入课题组邀请码" maxlength="8"
                  style="width: 240px" @keyup.enter="joinGroup" />
        <el-button type="primary" :loading="joining" @click="joinGroup">加入课题组</el-button>
        <span class="gc-tip">向课题组负责人（教师）索取邀请码；可同时加入多个课题组，不加入不影响正常预约</span>
      </div>

      <div v-for="g in myJoined" :key="g.id" class="gc-joined">
        <div class="gc-joined-info">
          <span class="gc-joined-name">📌 {{ g.name }}</span>
          <span class="gc-meta">负责人：{{ g.ownerName }} · 成员 {{ g.memberCount }} 人</span>
        </div>
        <el-button size="small" type="danger" plain :loading="leavingId === g.id" @click="leaveGroup(g)">退出</el-button>
      </div>
      <el-empty v-if="!myJoined.length" description="暂未加入课题组" :image-size="60" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import {
  groupCreate, groupOwned, groupMembers, groupJoin, groupLeave, groupMine,
  groupRemoveMember, groupRegenerateCode
} from '../api'

const props = defineProps({
  /** 我加入的课题组列表（父组件传入，避免重复请求；传入为 null 时自行拉取） */
  myGroups: { type: Array, default: null },
  /** 教师名下课题组列表（父组件传入） */
  myGroupsOwned: { type: Array, default: null }
})
const emit = defineEmits(['changed'])
const store = useUserStore()

const ROLE_LABEL = {
  ADMIN: '管理员', TEACHER: '教师', SAFETY: '安全员',
  KEEPER: '设备保管员', STUDENT: '学生', USER: '学生'
}
const roleTagType = r => ({ TEACHER: 'success', ADMIN: 'danger', KEEPER: 'warning', SAFETY: 'info' }[r] || '')

const groups = ref(props.myGroupsOwned || [])
const newName = ref('')
const creating = ref(false)
const openedId = ref(null)
const members = ref([])
const memberLoading = ref(false)
const inviteCode = ref('')
const joining = ref(false)
const leavingId = ref(null)
const myJoined = ref(props.myGroups || [])

onMounted(async () => {
  try {
    if (store.isTeacher && !props.myGroupsOwned) {
      groups.value = await groupOwned()
    }
    if (!props.myGroups) {
      myJoined.value = (await groupMine()) || []
    }
  } catch { /* 静默 */ }
})

/* 父组件数据异步到达后同步 */
function syncProps() {
  if (props.myGroupsOwned) groups.value = props.myGroupsOwned
  if (props.myGroups) myJoined.value = props.myGroups
}
defineExpose({ syncProps })

async function createGroup() {
  if (!newName.value.trim()) return ElMessage.warning('请填写课题组名称')
  creating.value = true
  try {
    const g = await groupCreate({ name: newName.value.trim() })
    groups.value.push({ ...g, memberCount: 0 })
    newName.value = ''
    ElMessage.success(`课题组「${g.name}」创建成功，邀请码：${g.inviteCode}`)
    emit('changed')
  } finally {
    creating.value = false
  }
}

async function toggleMembers(g) {
  if (openedId.value === g.id) { openedId.value = null; return }
  openedId.value = g.id
  memberLoading.value = true
  try {
    members.value = await groupMembers(g.id)
  } finally {
    memberLoading.value = false
  }
}

async function copyCode(g) {
  try {
    await navigator.clipboard.writeText(g.inviteCode)
    ElMessage.success('邀请码已复制')
  } catch {
    ElMessage.info(`邀请码：${g.inviteCode}（请手动复制）`)
  }
}

async function regenCode(g) {
  const code = await groupRegenerateCode(g.id)
  g.inviteCode = code
  ElMessage.success(`新邀请码：${code}（旧邀请码已失效）`)
}

async function removeMember(g, m) {
  await ElMessageBox.confirm(
    '确定将该成员移出课题组？移出后该成员将无法再查看本组预约记录',
    '移出成员',
    { type: 'warning', confirmButtonText: '确定移出', cancelButtonText: '取消' })
  await groupRemoveMember(g.id, m.id)
  members.value = members.value.filter(x => x.id !== m.id)
  g.memberCount = Math.max(0, (g.memberCount || 1) - 1)
  ElMessage.success(`已将「${m.realName || m.username}」移出课题组`)
  emit('changed')
}

async function joinGroup() {
  if (!inviteCode.value.trim()) return ElMessage.warning('请输入邀请码')
  joining.value = true
  try {
    const g = await groupJoin(inviteCode.value.trim())
    ElMessage.success(`已加入课题组「${g.name}」`)
    inviteCode.value = ''
    try { myJoined.value = (await groupMine()) || [] } catch { /* 静默 */ }
    emit('changed')
  } finally {
    joining.value = false
  }
}

async function leaveGroup(g) {
  await ElMessageBox.confirm(
    `确定退出课题组「${g.name}」吗？退出后将不再共享该组成员的预约信息。`, '退出课题组', { type: 'warning' })
  leavingId.value = g.id
  try {
    await groupLeave(g.id)
    ElMessage.success('已退出课题组')
    try { myJoined.value = (await groupMine()) || [] } catch { /* 静默 */ }
    emit('changed')
  } finally {
    leavingId.value = null
  }
}
</script>

<style scoped>
.group-card { margin-top: 4px; }
.gc-sub { font-size: 12.5px; font-weight: 400; color: #777777; margin-left: 8px; }
.gc-create { display: flex; align-items: center; gap: 10px; margin: 6px 0 14px; flex-wrap: wrap; }
.gc-tip { font-size: 12px; color: #97a6b0; }
.gc-item { border: 1px solid #eaf0f3; border-radius: 12px; margin-bottom: 10px; overflow: hidden; }
.gc-head {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; cursor: pointer; background: #fbfdfd;
}
.gc-head:hover { background: #f4f8fa; }
.gc-name { font-weight: 700; color: #2f4a5c; font-size: 14.5px; }
.gc-code { font-size: 13px; color: #5c7484; }
.gc-code b { color: #3d7a5c; letter-spacing: 2px; font-family: Consolas, monospace; }
.gc-actions { margin-left: auto; }
.gc-arrow { color: #a8b8c2; transition: transform 0.2s; font-size: 16px; }
.gc-arrow.open { transform: rotate(90deg); }
.gc-members { border-top: 1px solid #eef3f5; padding: 8px 16px; min-height: 52px; }
.gc-member {
  display: flex; align-items: center; gap: 12px;
  padding: 7px 4px; border-bottom: 1px dashed #eef3f5;
}
.gc-member:last-child { border-bottom: none; }
.gc-member-name { font-weight: 600; color: #33475a; font-size: 13.5px; }
.gc-member-no { color: #8a99a3; font-size: 12.5px; }
.gc-member .el-button { margin-left: auto; }
.gc-join-title { font-size: 14px; font-weight: 700; color: #3d5566; margin: 18px 0 4px; }
.gc-join-sub { font-size: 12px; font-weight: 400; color: #97a6b0; margin-left: 8px; }
.gc-joined {
  display: flex; align-items: center; justify-content: space-between;
  background: linear-gradient(135deg, #eef7f2, #e2efe8);
  border: 1px solid #d4e6dc; border-radius: 12px; padding: 14px 18px;
  margin-bottom: 8px;
}
.gc-joined-name { font-weight: 700; color: #2f5c47; font-size: 15px; margin-right: 14px; }
.gc-meta { color: #5f7d6e; font-size: 13px; }
</style>
