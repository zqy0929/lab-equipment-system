<template>
  <div class="page-card">
    <div class="page-title">日志审计 <span class="title-en">Audit Log</span></div>
    <el-alert type="info" :closable="false" style="margin-bottom: 14px"
              title="自动记录所有关键操作（新增/修改设备、提交/审批/修改预约、权限变更等），仅支持查看，禁止删除与修改" />

    <div class="toolbar">
      <el-select v-model="action" placeholder="全部动作" clearable style="width: 190px" @change="load">
        <el-option v-for="(label, key) in ACTIONS" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索操作人 / 操作内容" clearable style="width: 230px"
                @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="#" width="70" />
      <el-table-column prop="username" label="操作人" width="120" />
      <el-table-column label="动作" width="140">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.action)" size="small">{{ ACTIONS[row.action] || row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="操作内容" min-width="380" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="createTime" label="操作时间" width="175" />
    </el-table>

    <el-pagination style="margin-top: 14px; justify-content: flex-end" background layout="total, prev, pager, next"
                   :total="total" :page-size="size" v-model:current-page="page" @current-change="load" />
  </div>
</template>

<script setup>
import { ref, onActivated } from 'vue'
import { oplogPage } from '../api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 15
const keyword = ref('')
const action = ref('')

const ACTIONS = {
  CREATE_RESERVATION: '提交预约',
  MODIFY_RESERVATION: '修改预约',
  APPROVE_RESERVATION: '审批通过',
  REJECT_RESERVATION: '驳回预约',
  CANCEL_RESERVATION: '取消预约',
  CLAIM_RESERVATION: '领用确认',
  RETURN_RESERVATION: '归还设备',
  AUTO_RELEASE: '自动释放',
  EXPIRE_RESERVATION: '预约过期',
  CREATE_EQUIPMENT: '新增设备',
  UPDATE_EQUIPMENT: '修改设备',
  DELETE_EQUIPMENT: '删除设备',
  CHANGE_ROLE: '角色变更',
  CHANGE_STATUS: '账号启停',
  CHANGE_BLACKLIST: '黑名单变更',
  REPORT_HAZARD: '隐患上报',
  HANDLE_HAZARD: '隐患处理',
  UPDATE_CONFIG: '修改系统配置',
  UPDATE_CATEGORY_RULE: '修改分类时长'
}
const actionTagType = a =>
  ({ APPROVE_RESERVATION: 'success', REJECT_RESERVATION: 'danger', AUTO_RELEASE: 'danger',
     DELETE_EQUIPMENT: 'danger', CHANGE_ROLE: 'warning', CHANGE_BLACKLIST: 'warning' }[a] || 'info')

async function load() {
  loading.value = true
  try {
    const data = await oplogPage({ keyword: keyword.value, action: action.value, page: page.value, size })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

onActivated(load)
</script>

<style scoped>
.title-en { font-size: 12.5px; font-weight: 400; color: #777777; letter-spacing: 0.5px; margin-left: 6px; }
</style>
