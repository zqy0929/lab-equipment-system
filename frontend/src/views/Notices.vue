<template>
  <div class="page-card">
    <div class="page-title">通知公告</div>
    <div v-if="store.isAdmin" class="toolbar">
      <el-button type="primary" @click="dialogVisible = true">+ 发布公告</el-button>
    </div>

    <el-empty v-if="!list.length" description="暂无公告" />
    <el-timeline v-else>
      <el-timeline-item v-for="n in list" :key="n.id" :timestamp="fmt(n.createTime)" placement="top">
        <div class="notice-card">
          <div class="notice-head">
            <span class="notice-title">{{ n.title }}</span>
            <div>
              <el-tag size="small" type="info">{{ n.publisherName || '管理员' }}</el-tag>
              <el-button v-if="store.isAdmin" size="small" type="danger" link style="margin-left: 8px"
                         @click="remove(n)">删除</el-button>
            </div>
          </div>
          <div class="notice-content">{{ n.content }}</div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>

  <el-dialog v-model="dialogVisible" title="发布公告" width="520px">
    <el-form label-width="70px">
      <el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="内容" required>
        <el-input v-model="form.content" type="textarea" :rows="5" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="publish">发布</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { noticeList, noticeAdd, noticeDelete } from '../api'
import { useUserStore } from '../store/user'

const store = useUserStore()
const list = ref([])
const dialogVisible = ref(false)
const form = reactive({ title: '', content: '' })
const fmt = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')

async function load() {
  list.value = await noticeList()
}

async function publish() {
  if (!form.title || !form.content) return ElMessage.warning('请填写标题和内容')
  await noticeAdd(form)
  dialogVisible.value = false
  form.title = ''
  form.content = ''
  ElMessage.success('发布成功')
  load()
}

async function remove(n) {
  await ElMessageBox.confirm(`确定删除公告「${n.title}」吗？`, '提示', { type: 'warning' })
  await noticeDelete(n.id)
  ElMessage.success('删除成功')
  load()
}

onActivated(load)
</script>

<style scoped>
.notice-card {
  border: 1px solid #eaf0f3;
  border-radius: 12px;
  padding: 16px 18px;
  background: #ffffff;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.notice-card:hover {
  border-color: #cddde5;
  box-shadow: 0 4px 16px rgba(150, 165, 175, 0.1);
}
.notice-head { display: flex; justify-content: space-between; align-items: center; }
.notice-title { font-size: 15px; font-weight: 700; color: #333333; letter-spacing: 0.2px; }
.notice-content { margin-top: 9px; font-size: 14px; color: #333333; line-height: 1.85; white-space: pre-wrap; }
</style>
