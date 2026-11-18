<template>
  <el-container class="layout">
    <el-aside width="260px" class="aside">
      <div class="logo">
        <img class="logo-icon" :src="icons.logo" alt="" />
        <div class="logo-text">
          <span class="logo-cn">实验室设备管理</span>
          <span class="logo-en">Lab System</span>
        </div>
      </div>
      <nav class="menu">
        <div v-for="m in visibleMenu" :key="m.path"
             class="menu-card" :class="[`grad-${m.grad}`, { 'is-active': $route.path === m.path }]"
             @click="router.push(m.path)">
          <img class="menu-icon" :src="icons[m.icon]" alt="" />
          <div class="menu-text">
            <span class="menu-cn">{{ m.cn }}</span>
            <span class="menu-en">{{ m.en }}</span>
          </div>
          <!-- 借用审批待办角标：仅管理员/保管员可见，数量为 0 时隐藏 -->
          <span v-if="m.path === '/approval' && store.canApprove && pendingCount > 0"
                class="menu-badge">{{ pendingCount > 99 ? '99+' : pendingCount }}</span>
        </div>

        <!-- 系统管理折叠组：仅管理员可见（设备维护管理下方、个人中心上方） -->
        <div v-if="isAdmin" class="menu-card group-head"
             :class="['grad-settings', { 'is-active': inGroup }]"
             @click="groupOpen = !groupOpen">
          <img class="menu-icon" :src="icons[adminGroup.icon]" alt="" />
          <div class="menu-text">
            <span class="menu-cn">{{ adminGroup.cn }}</span>
            <span class="menu-en">{{ adminGroup.en }}</span>
          </div>
          <svg class="group-arrow" :class="{ 'is-open': groupOpen }" viewBox="0 0 24 24">
            <path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2.2"
                  stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <div v-if="isAdmin" class="submenu-wrap" :class="{ 'is-open': groupOpen }">
          <div class="submenu">
            <div v-for="c in adminGroup.children" :key="c.path"
                 class="submenu-item" :class="{ 'is-active': $route.path === c.path }"
                 @click="router.push(c.path)">
              <img class="submenu-icon" :src="icons[c.icon]" alt="" />
              <span class="submenu-cn">{{ c.cn }}</span>
              <span class="submenu-en">{{ c.en }}</span>
            </div>
          </div>
        </div>
      </nav>
      <!-- 固定在菜单最下方：个人中心（不随上方菜单滚动，任何角色都可见） -->
      <div class="menu-pinned">
        <div v-for="m in visiblePinned" :key="m.path"
             class="menu-card" :class="[`grad-${m.grad}`, { 'is-active': $route.path === m.path }]"
             @click="router.push(m.path)">
          <img class="menu-icon" :src="icons[m.icon]" alt="" />
          <div class="menu-text">
            <span class="menu-cn">{{ m.cn }}</span>
            <span class="menu-en">{{ m.en }}</span>
          </div>
        </div>
      </div>
      <div class="aside-foot">Lab Equipment · v2.0</div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">
          <span class="title-pill">{{ $route.meta.title || '' }}</span>
        </div>
        <div class="header-right">
          <!-- 消息铃铛 -->
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="bell-badge">
            <el-icon class="bell" :size="20" @click="openMessages">
              <Bell />
            </el-icon>
          </el-badge>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              {{ store.displayName }}
              <span class="role-pill" :class="store.isAdmin ? 'role-admin' : 'role-normal'">
                {{ store.roleLabel }}
              </span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main" :style="mainBgStyle">
        <!-- keep-alive 缓存已访问页面：切回时零重载（各页面在 onActivated 中静默刷新数据保证新鲜度） -->
        <router-view v-slot="{ Component }">
          <keep-alive :max="12">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>

    <!-- 消息中心弹窗 -->
    <el-dialog v-model="msgVisible" title="消息中心 Message Center" width="520px" class="msg-dialog">
      <div class="msg-toolbar">
        <span class="msg-unread-tip">未读 {{ unreadCount }} 条</span>
        <el-button size="small" type="primary" link :disabled="unreadCount === 0" @click="readAll">
          全部已读
        </el-button>
      </div>
      <div class="msg-list" v-loading="msgLoading">
        <div v-for="m in messages" :key="m.id" class="msg-item" :class="{ 'is-unread': m.isRead === 0 }"
             @click="readOne(m)">
          <div class="msg-item-head">
            <span class="msg-dot" v-if="m.isRead === 0"></span>
            <span class="msg-title">{{ m.title }}</span>
            <span class="msg-time">{{ fmtTime(m.createTime) }}</span>
          </div>
          <div class="msg-content">{{ m.content }}</div>
        </div>
        <el-empty v-if="!msgLoading && messages.length === 0" description="暂无消息" :image-size="70" />
      </div>
      <el-pagination small background layout="prev, pager, next" :total="msgTotal" :page-size="msgQuery.size"
                     v-model:current-page="msgQuery.page" @current-change="loadMessages" class="msg-pager" />
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { useUserStore } from '../store/user'
import { getMe, approvalPage, messagePage, messageUnreadCount, messageRead, messageReadAll } from '../api'
import { computed } from 'vue'

/* 侧边栏贴纸图标 */
import logoPng from '../assets/menu/logo.png'
import dashboardPng from '../assets/menu/dashboard.png'
import equipmentPng from '../assets/menu/equipment.png'
import calendarPng from '../assets/menu/calendar.png'
import myResPng from '../assets/menu/my-res.png'
import approvalPng from '../assets/menu/approval.png'
import damagePng from '../assets/menu/damage.png'
import noticesPng from '../assets/menu/notices.png'
import configPng from '../assets/menu/config.png'
import creditsPng from '../assets/menu/credits.png'
import usersPng from '../assets/menu/users.png'
import manualSvg from '../assets/menu/manual.svg'
import wrenchSvg from '../assets/menu/wrench.svg'
import profileSvg from '../assets/menu/profile.svg'
import settingsSvg from '../assets/menu/settings.svg'
const icons = {
  logo: logoPng, dashboard: dashboardPng, equipment: equipmentPng, calendar: calendarPng,
  'my-res': myResPng, approval: approvalPng, damage: damagePng, notices: noticesPng,
  config: configPng, credits: creditsPng, users: usersPng, manual: manualSvg, wrench: wrenchSvg,
  profile: profileSvg, settings: settingsSvg
}

/* 菜单卡片：grad 为渐变色系编号；roles 控制各角色可见性（侧边栏按登录角色动态渲染）
   学生：看板/名录/日历/我的预约/损耗/手册/公告（隐藏借用审批；名录、手册只读）
   教师：同学生（无审批权限）；安全员：+安全中心
   设备保管员：全部原有菜单 + 设备维护管理（审批 / 损耗上报 / 手册编辑 / 设备维护）
   管理员：一级菜单 = 保管员菜单；安全中心/系统配置/信用管理/日志审计/用户管理
           收入可折叠【系统管理】组（设备维护管理下方、个人中心上方） */
const ALL_ROLES = ['STUDENT', 'TEACHER', 'SAFETY', 'KEEPER', 'ADMIN']
const menuItems = [
  { path: '/dashboard',      cn: '数据看板',   en: 'Dashboard',    icon: 'dashboard', grad: 'blue',   roles: ALL_ROLES },
  { path: '/equipment',      cn: '设备名录',   en: 'Equipment',    icon: 'equipment', grad: 'teal',   roles: ALL_ROLES },
  { path: '/calendar',       cn: '预约日历',   en: 'Calendar',     icon: 'calendar',  grad: 'amber',  roles: ALL_ROLES },
  { path: '/my-reservations',cn: '我的预约',   en: 'Booking',      icon: 'my-res',    grad: 'sky',    roles: ALL_ROLES },
  { path: '/approval',       cn: '借用审批',   en: 'Approval',     icon: 'approval',  grad: 'purple', roles: ['ADMIN', 'KEEPER'] },
  { path: '/damage',         cn: '损耗记录',   en: 'Damage',       icon: 'damage',    grad: 'rose',   roles: ALL_ROLES },
  { path: '/manual',         cn: '设备手册',   en: 'Manual',       icon: 'manual',    grad: 'sage',   roles: ALL_ROLES },
  { path: '/maintenance',    cn: '设备维护管理', en: 'Maintenance', icon: 'wrench',    grad: 'wrench', roles: ['ADMIN', 'KEEPER'] },
  { path: '/notices',        cn: '通知公告',   en: 'Notice',       icon: 'notices',   grad: 'sand',   roles: ALL_ROLES },
  { path: '/safety',         cn: '安全中心',   en: 'Safety',       icon: 'approval',  grad: 'olive',  roles: ['SAFETY'] }
]
/* 系统管理折叠组：仅管理员显示（其余角色不渲染）；安全员的安全中心仍保持一级菜单不变 */
const adminGroup = {
  cn: '系统管理', en: 'System', icon: 'settings', grad: 'settings', roles: ['ADMIN'],
  children: [
    { path: '/safety',  cn: '安全中心', en: 'Safety',    icon: 'approval', grad: 'olive' },
    { path: '/config',  cn: '系统配置', en: 'Setting',   icon: 'config',   grad: 'mint' },
    { path: '/credits', cn: '信用管理', en: 'Credit',    icon: 'credits',  grad: 'coral' },
    { path: '/audit',   cn: '日志审计', en: 'Audit Log', icon: 'users',    grad: 'slate' },
    { path: '/users',   cn: '用户管理', en: 'Users',     icon: 'users',    grad: 'steel' }
  ]
}
/* 个人中心：固定在菜单最下方（独立于上方可滚动区域），所有角色均可见 */
const pinnedMenu = [
  { path: '/profile', cn: '个人中心', en: 'Profile', icon: 'profile', grad: 'person', roles: ALL_ROLES }
]
const visibleMenu = computed(() =>
  menuItems.filter(m => m.roles.includes(store.user?.role)))
const visiblePinned = computed(() =>
  pinnedMenu.filter(m => m.roles.includes(store.user?.role)))

const store = useUserStore()
const router = useRouter()
const route = useRoute()

/* ---------- 系统管理折叠组（仅管理员） ---------- */
const isAdmin = computed(() => store.user?.role === 'ADMIN')
const inGroup = computed(() =>
  isAdmin.value && adminGroup.children.some(c => c.path === route.path))
const groupOpen = ref(false)
// 当前页面属于系统管理组时自动展开，保证刷新/切页后状态一致
watch(() => route.path, p => {
  if (adminGroup.children.some(c => c.path === p)) groupOpen.value = true
}, { immediate: true })

/* ---------- 页面主区域水彩背景：不同页面不同插画，白色叠加层保证可读 ----------
   f 后缀 = 原图水平镜像版：原图主体（望远镜/小女孩）在画面左侧，
   会被内容卡片挡住；镜像后主体落到右下空白区，装饰更醒目。
   注意：bg-5 火箭男孩主体在原图边缘被裁切，镜像后仍切边，故弃用于空白大的页面 */
const BG_MAP = {
  '/dashboard': '/images/bg-1.png',
  '/equipment': '/images/bg-2.png',
  '/calendar': '/images/bg-3.png',
  '/my-reservations': '/images/bg-4.png',
  '/approval': '/images/bg-4.png',
  '/damage': '/images/bg-1.png',
  '/manual': '/images/bg-2.png',
  '/maintenance': '/images/bg-2f.png',
  '/notices': '/images/bg-4.png',
  '/config': '/images/bg-2f.png',
  '/credits': '/images/bg-4.png',
  '/users': '/images/bg-3f.png',
  '/profile': '/images/bg-3f.png'
}
const mainBgStyle = computed(() => {
  const img = BG_MAP[route.path] || '/images/bg-1.png'
  // 镜像版（f 后缀）主体在右侧：锚点右对齐，避免 cover 缩放把主体裁掉；
  // 被裁的左侧原本就在侧边栏下方，无损失
  return {
    backgroundImage: `linear-gradient(rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0.5)), url('${img}')`,
    backgroundPosition: img.endsWith('f.png') ? 'right center' : 'center'
  }
})

/* ---------- 消息中心 ---------- */
const msgVisible = ref(false)
const msgLoading = ref(false)
const messages = ref([])
const msgTotal = ref(0)
const unreadCount = ref(0)
const msgQuery = reactive({ page: 1, size: 8 })
let pollTimer = null
let approvalTimer = null

const fmtTime = t => (t ? dayjs(t).format('MM-DD HH:mm') : '-')

/* ---------- 借用审批待办角标（仅管理员/保管员） ---------- */
const pendingCount = ref(0)
async function loadPendingCount() {
  if (!store.canApprove) { pendingCount.value = 0; return }
  try {
    const data = await approvalPage({ status: 'PENDING', page: 1, size: 1 })
    pendingCount.value = Number(data?.total || 0)
  } catch { /* 静默：角标保持上次数量 */ }
}
// 切换页面时立即刷新 + 30 秒轮询兜底（学生在别端提交/审批操作后数字自动联动）
watch(() => route.path, () => loadPendingCount())

async function loadUnread() {
  try {
    unreadCount.value = await messageUnreadCount()
  } catch { /* 静默 */ }
}

async function loadMessages() {
  msgLoading.value = true
  try {
    const pageData = await messagePage(msgQuery)
    messages.value = pageData.records || []
    msgTotal.value = Number(pageData.total || 0)
  } finally {
    msgLoading.value = false
  }
}

async function openMessages() {
  msgVisible.value = true
  msgQuery.page = 1
  await loadUnread()
  // 打开消息中心即视为已读，无需手动点击；「全部已读」按钮保留用于批量兜底
  if (unreadCount.value > 0) {
    try {
      await messageReadAll()
      await loadUnread()
    } catch { /* 静默 */ }
  }
  loadMessages()
}

async function readOne(m) {
  if (m.isRead === 0) {
    await messageRead(m.id)
    m.isRead = 1
    loadUnread()
  }
}

async function readAll() {
  await messageReadAll()
  loadMessages()
  loadUnread()
  ElMessage.success('已全部标记为已读')
}

onMounted(async () => {
  // 刷新 store 中的用户信息
  try {
    const me = await getMe()
    if (me) store.setUser(me)
  } catch (e) { /* 拦截器已处理 */ }
  loadUnread()
  loadPendingCount()
  // 审批通过/驳回、学生提交/取消预约后立即刷新角标（各页面派发 pending-count-changed 事件）
  window.addEventListener('pending-count-changed', loadPendingCount)
  // 每 60 秒轮询未读数，保持红点及时
  pollTimer = setInterval(loadUnread, 60 * 1000)
  // 每 30 秒轮询待审批数量，保证角标与实际待办联动
  approvalTimer = setInterval(loadPendingCount, 30 * 1000)
  // 空闲时预加载全部页面背景大图（最大约 900KB），消除切换页面时的图片下载/解码卡顿
  const preload = () => [...new Set(Object.values(BG_MAP))].forEach(src => { new Image().src = src })
  if ('requestIdleCallback' in window) requestIdleCallback(preload, { timeout: 4000 })
  else setTimeout(preload, 1500)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (approvalTimer) clearInterval(approvalTimer)
  window.removeEventListener('pending-count-changed', loadPendingCount)
})

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
      .then(() => {
        store.logout()
        router.push('/login')
      })
      .catch(() => {})
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.layout { height: 100%; }

/* 侧边栏：冷调浅灰蓝底，菜单为独立渐变小卡片 */
.aside {
  background: #f5f8f9;
  border-right: 1px solid #e6edf0;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border-bottom: 1px solid #e6edf0;
  flex-shrink: 0;
}
.logo-icon { width: 34px; height: 34px; object-fit: contain; }
.logo-text { display: flex; flex-direction: column; line-height: 1.25; }
.logo-cn { color: #333333; font-size: 16px; font-weight: 700; letter-spacing: 0.5px; }
.logo-en { color: #777777; font-size: 12px; font-weight: 500; letter-spacing: 1.4px; text-transform: uppercase; }

.menu {
  flex: 1;
  overflow-y: auto;
  padding: 12px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.menu-card {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1 1 0;
  min-height: 48px;
  max-height: 62px;
  padding: 0 12px;
  border-radius: 11px;
  cursor: pointer;
  user-select: none;
  border: 1.5px solid transparent;
  box-shadow: 0 1px 3px rgba(150, 165, 175, 0.10);
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}
.menu-card:hover { transform: translateY(-1px); box-shadow: 0 3px 8px rgba(150, 165, 175, 0.22); }

/* 借用审批待办角标：莫兰迪浅红小圆标，贴卡片右上角，不遮文字与图标 */
.menu-card { position: relative; }
.menu-badge {
  position: absolute;
  top: -7px;
  right: -6px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #c98d8d, #b07272);
  border: 2px solid #f5f8f9;
  box-shadow: 0 2px 6px rgba(176, 114, 114, 0.42);
  pointer-events: none;
  z-index: 2;
  letter-spacing: 0.2px;
}

/* 菜单最下方固定区（个人中心）：不随上方菜单滚动，始终贴底可见 */
.menu-pinned {
  flex-shrink: 0;
  padding: 8px 12px 2px;
  margin-top: 2px;
  border-top: 1px dashed #e3eaee;
}
.menu-pinned .menu-card { min-height: 48px; max-height: 62px; }
.menu-card.is-active {
  border-color: rgba(255, 255, 255, 0.95);
  box-shadow: inset 0 0 0 3px rgba(56, 86, 106, 0.85), 0 5px 14px rgba(96, 118, 132, 0.42);
  transform: scale(1.03);
  filter: saturate(1.2) brightness(1.02);
  outline: 1.5px solid rgba(255, 255, 255, 0.9);
  outline-offset: -5px;
}
.menu-icon { width: 30px; height: 30px; object-fit: contain; flex-shrink: 0; filter: drop-shadow(0 1px 1px rgba(120, 140, 155, 0.25)); }
.menu-text { display: flex; flex-direction: column; line-height: 1.3; min-width: 0; }
.menu-cn { font-size: 15px; font-weight: 600; color: #2f3a41; }
.menu-en { font-size: 12px; font-weight: 500; color: rgba(47, 58, 65, 0.72); letter-spacing: 0.5px; }
.menu-card.is-active .menu-cn { font-weight: 800; color: #22303a; }
.menu-card.is-active .menu-en { color: rgba(34, 48, 58, 0.85); font-weight: 600; }
.aside-foot {
  flex-shrink: 0;
  text-align: center;
  padding: 10px 0 12px;
  font-size: 12px;
  letter-spacing: 0.8px;
  color: #777777;
  border-top: 1px dashed #e6edf0;
}

/* 莫兰迪渐变色系 */
.grad-blue   { background: linear-gradient(135deg, #dce8f0, #b9cfdc); }
.grad-teal   { background: linear-gradient(135deg, #dcebe3, #b8d4c6); }
.grad-amber  { background: linear-gradient(135deg, #f4e4d8, #e2c5ae); }
.grad-sky    { background: linear-gradient(135deg, #e2edf4, #c2d6e3); }
.grad-purple { background: linear-gradient(135deg, #e6e2f2, #c9c3e0); }
.grad-rose   { background: linear-gradient(135deg, #f2dede, #ddb3b3); }
.grad-sand   { background: linear-gradient(135deg, #f2e9da, #ddcdaa); }
.grad-mint   { background: linear-gradient(135deg, #dde9e5, #b7cdc5); }
.grad-coral  { background: linear-gradient(135deg, #f6e3d7, #e6bfa4); }
.grad-steel  { background: linear-gradient(135deg, #e0e5ee, #c0c9da); }
.grad-olive  { background: linear-gradient(135deg, #e7ecda, #ccd8b4); }
.grad-slate  { background: linear-gradient(135deg, #e3e8ea, #c5ced4); }
.grad-sage   { background: linear-gradient(135deg, #e3ece4, #c2d6c5); }
/* 设备维护管理：莫兰迪灰绿 */
.grad-wrench { background: linear-gradient(135deg, #e6eee8, #bacdc0); }
/* 个人中心：莫兰迪雾紫 */
.grad-person { background: linear-gradient(135deg, #ece9f2, #cbc5d9); }
/* 系统管理折叠组：莫兰迪雾蓝灰 */
.grad-settings { background: linear-gradient(135deg, #e4e9ee, #c3ccd6); }

/* 系统管理折叠组（仅管理员）：一级卡右侧展开箭头 + 可收起子菜单 */
.group-arrow {
  margin-left: auto;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: rgba(47, 58, 65, 0.6);
  transition: transform 0.25s ease;
}
.group-arrow.is-open { transform: rotate(180deg); }
.submenu-wrap {
  flex: 0 0 auto;
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  transition: max-height 0.28s ease, opacity 0.2s ease;
}
.submenu-wrap.is-open { max-height: 300px; opacity: 1; }
.submenu {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 8px;
  margin-top: 2px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid #e2eaee;
  border-radius: 11px;
  box-shadow: 0 1px 3px rgba(150, 165, 175, 0.08) inset;
}
.submenu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  background: #f2f6f7;
  border: 1.5px solid transparent;
  transition: background-color 0.18s, border-color 0.18s, transform 0.18s;
}
.submenu-item:hover { background: #e8eff2; transform: translateX(2px); }
.submenu-item.is-active {
  background: #e6eef3;
  border-color: rgba(56, 86, 106, 0.5);
}
.submenu-icon { width: 20px; height: 20px; object-fit: contain; flex-shrink: 0; }
.submenu-cn { font-size: 13.5px; font-weight: 600; color: #2f3a41; }
.submenu-item.is-active .submenu-cn { font-weight: 800; color: #22303a; }
.submenu-en { margin-left: auto; font-size: 11px; font-weight: 500; color: rgba(47, 58, 65, 0.55); letter-spacing: 0.4px; }
.submenu-item.is-active .submenu-en { color: rgba(34, 48, 58, 0.7); }

/* 顶栏：纯白，浅描边 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #ecf1f4;
  box-shadow: 0 1px 8px rgba(150, 165, 175, 0.06);
  z-index: 1;
}
.header-title {
  display: flex;
  align-items: center;
}
/* 页面标题气泡 */
.title-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 18px;
  border-radius: 999px;
  background: linear-gradient(135deg, #e9f1f5, #d5e2ea);
  border: 1px solid #cfdfe8;
  color: #26323a;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
  box-shadow: 0 2px 6px rgba(140, 160, 172, 0.18);
}
.title-pill::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #8fa9bc, #6f8fa5);
  box-shadow: 0 0 0 3px rgba(143, 169, 188, 0.25);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.bell-badge { display: flex; }
.bell {
  color: #5c666d;
  cursor: pointer;
  transition: color 0.2s, transform 0.2s;
}
.bell:hover { color: #5f7e90; transform: scale(1.1); }
.user-info {
  cursor: pointer;
  font-size: 14.5px;
  font-weight: 600;
  color: #2f3a41;
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 角色气泡：管理员雾蓝渐变，其余灰紫 */
.role-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(120, 140, 155, 0.30);
}
.role-admin { background: linear-gradient(135deg, #9db8c9, #6f8fa5); }
.role-normal { background: linear-gradient(135deg, #b5b1c4, #8d89a0); }
.main {
  background-color: #f1f5f6;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
  padding: 22px;
  overflow: auto;
}

/* 个人信息信用分 */
.credit-display { display: flex; align-items: baseline; gap: 10px; }
.credit-score {
  font-size: 22px;
  font-weight: 700;
  color: #5f7e90;
}
.credit-score.credit-low { color: #bc9494; }
.credit-tip { font-size: 12.5px; color: #666666; }

/* 消息中心 */
.msg-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.msg-unread-tip { font-size: 13px; font-weight: 500; color: #666666; }
.msg-list {
  max-height: 380px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.msg-item {
  border: 1px solid #ecf1f4;
  border-radius: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}
.msg-item:hover { border-color: #bcd0da; background: #f7fafb; }
.msg-item.is-unread { background: #eef5f8; border-color: #dbe7ee; }
.msg-item-head { display: flex; align-items: center; gap: 8px; }
.msg-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #bc9494;
  flex-shrink: 0;
}
.msg-title {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #333333;
}
.msg-item:not(.is-unread) .msg-title { font-weight: 500; color: #4a5257; }
.msg-time { font-size: 12px; color: #777777; flex-shrink: 0; }
.msg-content {
  margin-top: 5px;
  font-size: 13px;
  color: #666666;
  line-height: 1.6;
  word-break: break-all;
}
.msg-pager { margin-top: 12px; justify-content: center; }
</style>
