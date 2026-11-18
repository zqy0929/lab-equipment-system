import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

/** 角色全集：学生 / 教师 / 安全员 / 设备保管员 / 管理员 */
const ALL = ['STUDENT', 'TEACHER', 'SAFETY', 'KEEPER', 'ADMIN']

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板', roles: ALL } },
      { path: 'equipment', name: 'Equipment', component: () => import('../views/Equipment.vue'), meta: { title: '设备名录', roles: ALL } },
      { path: 'calendar', name: 'Calendar', component: () => import('../views/CalendarView.vue'), meta: { title: '预约日历', roles: ALL } },
      { path: 'my-reservations', name: 'MyReservations', component: () => import('../views/MyReservations.vue'), meta: { title: '我的预约', roles: ALL } },
      { path: 'approval', name: 'Approval', component: () => import('../views/Approval.vue'), meta: { title: '借用审批', roles: ['ADMIN', 'KEEPER'] } },
      { path: 'damage', name: 'Damage', component: () => import('../views/Damage.vue'), meta: { title: '损耗记录', roles: ALL } },
      { path: 'manual', name: 'Manual', component: () => import('../views/Manual.vue'), meta: { title: '设备手册', roles: ALL } },
      { path: 'maintenance', name: 'Maintenance', component: () => import('../views/Maintenance.vue'), meta: { title: '设备维护管理', roles: ['ADMIN', 'KEEPER'] } },
      { path: 'notices', name: 'Notices', component: () => import('../views/Notices.vue'), meta: { title: '通知公告', roles: ALL } },
      { path: 'config', name: 'SystemConfig', component: () => import('../views/SystemConfig.vue'), meta: { title: '系统配置', roles: ['ADMIN'] } },
      { path: 'credits', name: 'CreditManage', component: () => import('../views/CreditManage.vue'), meta: { title: '信用管理', roles: ['ADMIN'] } },
      { path: 'safety', name: 'SafetyCenter', component: () => import('../views/SafetyCenter.vue'), meta: { title: '安全中心', roles: ['ADMIN', 'SAFETY'] } },
      { path: 'audit', name: 'AuditLog', component: () => import('../views/AuditLog.vue'), meta: { title: '日志审计', roles: ['ADMIN'] } },
      { path: 'users', name: 'Users', component: () => import('../views/Users.vue'), meta: { title: '用户管理', roles: ['ADMIN'] } },
      { path: 'profile', name: 'Profile', component: () => import('../views/PersonalCenter.vue'), meta: { title: '个人中心', roles: ALL } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局守卫：未登录跳转登录页；禁止访问当前角色无权限的页面（防 URL 越权）
router.beforeEach(to => {
  const store = useUserStore()
  if (to.path !== '/login' && !store.token) return '/login'
  if (to.meta.roles && !to.meta.roles.includes(store.user?.role)) return '/dashboard'
  return true
})

export default router
