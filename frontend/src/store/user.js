import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isAdmin: state => state.user?.role === 'ADMIN',
    /** 审批权限：管理员与设备保管员（保管员为唯一审批负责人，教师无审批权限） */
    canApprove: state => state.user?.role === 'ADMIN' || state.user?.role === 'KEEPER',
    /** 设备/损耗管理：管理员与设备保管员 */
    canManage: state => state.user?.role === 'ADMIN' || state.user?.role === 'KEEPER',
    /** 安全中心：管理员与安全员 */
    isSafety: state => state.user?.role === 'ADMIN' || state.user?.role === 'SAFETY',
    /** 教师：可批量为本班学生代提交预约 */
    isTeacher: state => state.user?.role === 'TEACHER',
    /** 学生：数据可见域收窄为仅本人（日历 / 看板 / 损耗 / 导出） */
    isStudent: state => state.user?.role === 'STUDENT' || !state.user?.role,
    roleLabel: state => {
      // 角色枚举唯一：历史值 USER 归一为学生（STUDENT）
      const role = state.user?.role === 'USER' ? 'STUDENT' : state.user?.role
      return ({
        ADMIN: '管理员', TEACHER: '教师', SAFETY: '安全员', KEEPER: '设备保管员',
        STUDENT: '学生'
      }[role] || '学生')
    },
    displayName: state =>
      state.user?.realName || state.user?.username || '未登录'
  },
  actions: {
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify(user))
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
