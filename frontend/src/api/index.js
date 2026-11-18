import request from './request'

// ---------- 认证 ----------
export const login = data => request.post('/auth/login', data)
export const register = data => request.post('/auth/register', data)
export const authCaptcha = () => request.get('/auth/captcha')
export const getMe = () => request.get('/profile/me')
export const updateProfile = data => request.put('/profile/update', data)
export const changePassword = data => request.put('/profile/password', data)

// ---------- 设备 ----------
export const equipmentPage = params => request.get('/equipment/page', { params })
export const equipmentCategories = () => request.get('/equipment/categories')
export const equipmentAvailable = () => request.get('/equipment/available')
export const equipmentDetail = id => request.get(`/equipment/${id}`)
export const equipmentAdd = data => request.post('/admin/equipment', data)
export const equipmentUpdate = data => request.put('/admin/equipment', data)
export const equipmentDelete = id => request.delete(`/admin/equipment/${id}`)
export const equipmentStatusUpdate = (id, status) => request.put(`/admin/equipment/${id}/status`, { status })

// ---------- 预约 ----------
export const reservationCreate = data => request.post('/reservations', data)
export const reservationMy = params => request.get('/reservations/my', { params })
export const reservationCalendar = month => request.get('/reservations/calendar', { params: { month } })
/** 当前用户同时预约额度：{ maxActive, activeCount, remaining, atLimit } */
export const reservationQuota = () => request.get('/reservations/quota')
export const reservationCancel = id => request.put(`/reservations/${id}/cancel`)
/** 我的取消统计：近 30 天取消计次 / 阈值 / 是否封禁 / 剩余解禁天数 */
export const reservationCancelStats = () => request.get('/reservations/cancel-stats')
/** 管理员：全体用户取消记录 / 封禁名单 / 手动解除封禁（管理员与保管员） */
export const cancelRecordsPage = params => request.get('/admin/reservations/cancel-records', { params })
export const cancelBansList = () => request.get('/admin/reservations/cancel-bans')
export const cancelBanLift = userId => request.put(`/admin/reservations/cancel-ban/${userId}/lift`)
export const reservationModify = data => request.put('/reservations/modify', data)
export const reservationClaim = (id, formData) =>
  request.put(`/reservations/${id}/claim`, formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 })
export const reservationReturn = (id, formData) =>
  request.put(`/reservations/${id}/return`, formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 })
export const reservationPhotoUrl = (id, type) => `/api/reservations/${id}/photo?type=${type}`
export const reservationBatch = data => request.post('/reservations/batch', data)

// ---------- 候补队列 ----------
export const waitlistJoin = data => request.post('/reservations/waitlist', data)
export const waitlistMy = params => request.get('/reservations/waitlist/my', { params })
export const waitlistCancel = id => request.put(`/reservations/waitlist/${id}/cancel`)

// ---------- 审计日志（管理员，只读） ----------
export const oplogPage = params => request.get('/admin/oplogs/page', { params })

// ---------- 安全中心（安全员/管理员） ----------
export const safetyHighRisk = params => request.get('/safety/high-risk', { params })
export const safetyReadRecords = params => request.get('/safety/read-records', { params })
export const safetyHazards = params => request.get('/safety/hazards', { params })
export const safetyHandleHazard = (id, data) => request.put(`/safety/hazards/${id}/handle`, data)
export const hazardReport = data => request.post('/hazards', data)

// ---------- 用户黑名单 ----------
export const userBlacklist = (id, blacklisted) => request.put(`/admin/users/${id}/blacklist`, { blacklisted })
export const approvalPage = params => request.get('/admin/reservations/page', { params })
export const approvalSubmit = data => request.put('/admin/reservations/approve', data)

// ---------- 损耗 ----------
export const damageReport = data => request.post('/damages', data)
export const damagePage = params => request.get('/damages/page', { params })
export const damageHandle = data => request.put('/admin/damages/handle', data)

// ---------- 统计 ----------
export const statsDashboard = () => request.get('/stats/dashboard')

// ---------- 课题组（教师创建；成员凭邀请码加入，可同时加入多个） ----------
export const groupCreate = data => request.post('/groups', data)
export const groupOwned = () => request.get('/groups/owned')
export const groupMembers = id => request.get(`/groups/${id}/members`)
export const groupJoin = inviteCode => request.post('/groups/join', { inviteCode })
export const groupMine = () => request.get('/groups/mine')
export const groupLeave = id => request.post(`/groups/${id}/leave`)
export const groupRemoveMember = (id, memberId) => request.delete(`/groups/${id}/members/${memberId}`)
export const groupRegenerateCode = id => request.put(`/groups/${id}/invite-code`)

// ---------- 公告 ----------
export const noticeList = () => request.get('/notices')
export const noticeAdd = data => request.post('/admin/notices', data)
export const noticeDelete = id => request.delete(`/admin/notices/${id}`)

// ---------- 用户管理（管理员） ----------
export const userPage = params => request.get('/admin/users/page', { params })
export const userRole = (id, role) => request.put(`/admin/users/${id}/role`, { role })
export const userAudit = (id, action) => request.put(`/admin/users/${id}/audit`, { action })
export const userStatus = (id, status) => request.put(`/admin/users/${id}/status`, { status })
export const userResetPassword = (id, newPassword) => request.put(`/admin/users/${id}/password`, { newPassword })
export const userDelete = id => request.delete(`/admin/users/${id}`)

// ---------- 设备资料库附件 ----------
export const fileList = equipmentId => request.get('/equipment-files', { params: { equipmentId } })
export const fileDelete = id => request.delete(`/equipment-files/${id}`)
export const fileDownloadUrl = (id, inline = false) =>
  `/api/equipment-files/${id}/download${inline ? '?inline=true' : ''}`
export const uploadFile = formData =>
  request.post('/equipment-files', formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 })

// ---------- 站内消息 ----------
export const messagePage = params => request.get('/messages', { params })
export const messageUnreadCount = () => request.get('/messages/unread-count')
export const messageRead = id => request.put(`/messages/${id}/read`)
export const messageReadAll = () => request.put('/messages/read-all')

// ---------- 设备手册（全员可读，编辑仅管理员/保管员） ----------
export const manualList = (keyword, category) => request.get('/manuals', { params: { keyword, category } })
export const manualDetail = equipmentId => request.get(`/manuals/${equipmentId}`)
export const manualConfirmRead = equipmentId => request.post(`/manuals/${equipmentId}/read`)
export const manualSave = (equipmentId, data) => request.put(`/manuals/${equipmentId}`, data)
export const manualUploadAttachment = (equipmentId, formData) =>
  request.post(`/manuals/${equipmentId}/attachment`, formData,
    { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 })
export const manualDeleteAttachment = equipmentId => request.delete(`/manuals/${equipmentId}/attachment`)
export const manualAttachmentUrl = (equipmentId, inline = false) =>
  `/api/manuals/${equipmentId}/attachment${inline ? '?inline=true' : ''}`

// ---------- Excel 导出 ----------
export const exportReservations = params => request.get('/export/reservations', { params, responseType: 'blob', timeout: 60000 })
export const exportEquipment = params => request.get('/export/equipment', { params, responseType: 'blob', timeout: 60000 })
export const exportDamages = params => request.get('/export/damages', { params, responseType: 'blob', timeout: 60000 })

/** 触发浏览器下载 Blob */
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

// ---------- 系统配置（管理员） ----------
export const configGet = () => request.get('/admin/config')
export const configUpdate = data => request.put('/admin/config', data)

// ---------- 设备分类预约时长规则 ----------
export const categoryRuleList = () => request.get('/admin/category-rules')
export const categoryRuleUpdate = data => request.put('/admin/category-rules', data)
/** 只读：供预约页面展示分类时长限制提示 */
export const categoryRulePublic = () => request.get('/category-rules')

// ---------- 信用积分（管理员） ----------
export const creditUsers = params => request.get('/admin/credits/users', { params })
export const violationPage = params => request.get('/admin/credits/violations', { params })
export const violationCreate = data => request.post('/admin/credits/violations', data)

// ---------- 我的信用 ----------
export const myCredit = () => request.get('/credits/my')
export const myViolations = () => request.get('/credits/my/violations')

// ---------- 状态字典 ----------
export const EQUIPMENT_STATUS = {
  AVAILABLE: '正常',
  UNDER_REPAIR: '维修中',
  SCRAPPED: '停用'
}

export const RESERVATION_STATUS = {
  PENDING: '待审批',
  APPROVED: '借用中',
  CLAIMED: '使用中',
  REJECTED: '已驳回',
  RETURNED: '已归还',
  CANCELLED: '已取消',
  AUTO_RELEASED: '自动释放',
  EXPIRED: '已过期'
}

/** 设备标签（管理员可绑定，名录页可筛选） */
export const EQUIPMENT_TAGS = [
  '光学仪器', '电子测试设备', '高温设备', '危险化学品配套设备', '可外借', '仅限实验室使用'
]
/** 设备大类：名录页的分类筛选条件 */
export const EQUIPMENT_CATEGORY_TAGS = [
  '光学仪器', '电子测试设备', '高温设备', '危险化学品配套设备'
]
/** 使用属性标签：名录页的附加筛选条件（可与大类叠加） */
export const EQUIPMENT_USAGE_TAGS = ['可外借', '仅限实验室使用']
/** 高危标签（预约时强制安全须知） */
export const HIGH_RISK_TAGS = ['高温设备', '危险化学品配套设备']

export const DAMAGE_SEVERITY = {
  MINOR: '轻微',
  MODERATE: '中等',
  SEVERE: '严重'
}

export const DAMAGE_STATUS = {
  REPORTED: '待处理',
  FIXING: '维修中',
  FIXED: '已修复'
}
