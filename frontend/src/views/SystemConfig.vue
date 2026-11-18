<template>
  <div class="page-card" v-loading="loading">
    <div class="page-title">
      系统配置 <span class="title-en">System Settings · Reservation Rules</span>
      <span class="mode-badge" :class="editing ? 'mode-edit' : 'mode-view'">
        {{ editing ? '编辑中 · 修改后需保存生效' : '查看模式 · 只读' }}
      </span>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 18px"
              title="以下规则在用户提交预约和取消预约时自动校验，保存后立即生效" />

    <el-form :model="form" label-width="180px" class="config-form">
      <!-- ================= 全局规则 ================= -->
      <div class="section-title">
        <span class="bar"></span>全局预约规则
      </div>

      <el-form-item label="设备可提前预约最大天数">
        <el-input-number v-model="form.advance_days" :min="1" :max="90" :disabled="!editing" />
        <span class="form-tip">天 · 超出范围的开始日期将被拒绝</span>
      </el-form-item>
      <el-form-item label="免费取消截止时间">
        <el-input-number v-model="form.cancel_cutoff_minutes" :min="0" :max="720" :step="15" :disabled="!editing" />
        <span class="form-tip">分钟 · 预约开始前该时间段内不可取消</span>
      </el-form-item>
      <el-form-item label="单人最大同时预约数量">
        <el-input-number v-model="form.max_active_reservations" :min="1" :max="20" :disabled="!editing" />
        <span class="form-tip">
          台 · 用户同时持有的【待审批 + 借用中】预约达到该数量后，无法再提交新预约（默认 2 台）
        </span>
      </el-form-item>
      <el-form-item label="信用分预约阈值">
        <el-input-number v-model="form.credit_threshold" :min="0" :max="100" :disabled="!editing" />
        <span class="form-tip">分 · 低于该值的用户无法提交预约</span>
      </el-form-item>
      <el-form-item label="过期违约扣分">
        <el-input-number v-model="form.expire_deduction" :min="0" :max="50" :disabled="!editing" />
        <span class="form-tip">分 · 预约超过结束时间仍未完成（未提前取消）时扣减的信用分</span>
      </el-form-item>

      <!-- ================= 取消限制 ================= -->
      <div class="section-title">
        <span class="bar"></span>预约取消限制
        <span class="section-tip">
          30 天内主动取消超过阈值将限制提交新预约；计次规则：开始前 24 小时以上取消不计入，
          24 小时至 3 小时之间取消计 1 次，3 小时内取消计 2 次
        </span>
      </div>
      <el-form-item label="30 天取消次数阈值">
        <el-input-number v-model="form.cancel_limit_count" :min="1" :max="50" :disabled="!editing" />
        <span class="form-tip">
          次 · 30 天内取消计次超过该值后，限制用户提交新的设备预约（默认 5 次）
        </span>
      </el-form-item>
      <el-form-item label="取消超限封禁天数">
        <el-input-number v-model="form.cancel_ban_days" :min="1" :max="365" :disabled="!editing" />
        <span class="form-tip">
          天 · 取消次数超限后暂停提交新预约的天数（自最近一次计入取消的时间起算，默认 7 天）
        </span>
      </el-form-item>

      <!-- ================= 分类时长 ================= -->
      <div class="section-title">
        <span class="bar"></span>设备分类预约时长
        <span class="section-tip">按设备分类分别设置单次预约最短 / 最长时长，用户预约时按设备所属分类校验</span>
      </div>

      <el-table :data="rules" class="cat-table" size="default" border>
        <el-table-column prop="category" label="设备分类" width="150">
          <template #default="{ row }">
            <span class="cat-name">{{ row.category }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单次预约最短时长（分钟）" min-width="220">
          <template #default="{ row }">
            <el-input-number v-model="row.minDuration" :min="5" :max="1440" :step="5"
                             :disabled="!editing" size="default" controls-position="right"
                             style="width: 150px" />
          </template>
        </el-table-column>
        <el-table-column label="单次预约最长时长（分钟）" min-width="220">
          <template #default="{ row }">
            <el-input-number v-model="row.maxDuration" :min="5" :max="1440" :step="15"
                             :disabled="!editing" size="default" controls-position="right"
                             style="width: 150px" />
          </template>
        </el-table-column>
        <el-table-column label="可预约时长区间" width="200">
          <template #default="{ row }">
            <span class="range-text">{{ fmtRange(row) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="actions">
        <template v-if="!editing">
          <el-button type="primary" @click="startEdit">修改配置</el-button>
        </template>
        <template v-else>
          <el-button type="primary" :loading="saving" @click="save">保存配置 Save</el-button>
          <el-button @click="cancelEdit">取消修改</el-button>
        </template>
        <span class="action-tip" v-if="!editing">点击「修改配置」后可编辑以上全部规则</span>
        <span class="action-tip" v-else>修改完成后请点击「保存配置」，或点击「取消修改」放弃本次改动</span>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { configGet, configUpdate, categoryRuleList, categoryRuleUpdate } from '../api'

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)

const form = reactive({
  advance_days: 7,
  cancel_cutoff_minutes: 120,
  max_active_reservations: 2,
  credit_threshold: 60,
  expire_deduction: 5,
  cancel_limit_count: 5,
  cancel_ban_days: 7
})
const rules = ref([])

let formBackup = null
let rulesBackup = null

const fmtRange = row => {
  if (row.minDuration == null || row.maxDuration == null) return '-'
  return `${row.minDuration} ~ ${row.maxDuration} 分钟`
}

async function load() {
  loading.value = true
  try {
    const [data, list] = await Promise.all([configGet(), categoryRuleList()])
    for (const key of Object.keys(form)) {
      if (data[key] !== undefined) form[key] = Number(data[key])
    }
    rules.value = (list || []).map(r => ({
      category: r.category,
      minDuration: Number(r.minDuration ?? 30),
      maxDuration: Number(r.maxDuration ?? 240)
    }))
  } finally {
    loading.value = false
  }
}

onActivated(load)

/** 进入编辑模式：先备份当前值，供「取消修改」还原 */
function startEdit() {
  formBackup = JSON.parse(JSON.stringify(form))
  rulesBackup = JSON.parse(JSON.stringify(rules.value))
  editing.value = true
}

/** 取消修改：丢弃改动，恢复原值并回到查看模式 */
function cancelEdit() {
  if (formBackup) Object.assign(form, formBackup)
  if (rulesBackup) rules.value = JSON.parse(JSON.stringify(rulesBackup))
  editing.value = false
  ElMessage.info('已取消修改，配置恢复为修改前的值')
}

async function save() {
  if (form.max_active_reservations == null || form.max_active_reservations < 1) {
    return ElMessage.warning('单人最大同时预约数量至少为 1 台')
  }
  // 逐分类校验最短不能大于最长
  for (const r of rules.value) {
    if (r.minDuration == null || r.maxDuration == null) {
      return ElMessage.warning(`「${r.category}」的最短/最长时长不能为空`)
    }
    if (r.minDuration > r.maxDuration) {
      return ElMessage.warning(`「${r.category}」最短时长不能大于最长时长`)
    }
  }

  try {
    await ElMessageBox.confirm('确认保存系统预约规则？保存后立即生效', '保存确认', {
      confirmButtonText: '确认保存',
      cancelButtonText: '再检查一下',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }

  saving.value = true
  try {
    const payload = {}
    for (const key of Object.keys(form)) payload[key] = String(form[key])
    await configUpdate(payload)
    await categoryRuleUpdate(rules.value.map(r => ({
      category: r.category,
      minDuration: r.minDuration,
      maxDuration: r.maxDuration
    })))
    ElMessage.success('系统预约规则保存成功，已立即生效')
    editing.value = false
    await load()
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.title-en {
  font-size: 12.5px;
  font-weight: 400;
  color: #777777;
  letter-spacing: 0.5px;
  margin-left: 6px;
}
.mode-badge {
  margin-left: 12px;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 20px;
  vertical-align: middle;
  font-weight: 500;
}
.mode-view { color: #6f7d72; background: #eef2ef; border: 1px solid #d9e2dc; }
.mode-edit { color: #9c8168; background: #f8f1e7; border: 1px solid #e3cfb4; }

.config-form { max-width: 900px; padding-top: 6px; }

.section-title {
  display: flex;
  align-items: center;
  font-size: 14.5px;
  font-weight: 600;
  color: #3f4a44;
  margin: 6px 0 16px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e6e2da;
}
.section-title .bar {
  width: 3px;
  height: 15px;
  border-radius: 2px;
  background: linear-gradient(180deg, #b9a68d, #9c8168);
  margin-right: 8px;
}
.section-tip {
  font-size: 12px;
  font-weight: 400;
  color: #8a8a8a;
  margin-left: 10px;
}

.cat-table { margin-bottom: 8px; --el-table-border-color: #ece7de; }
.cat-name { font-weight: 500; color: #4a4a4a; }
.range-text { font-size: 13px; color: #7d8b81; }

.form-tip {
  margin-left: 12px;
  font-size: 13px;
  color: #666666;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid #f0ece4;
}
.action-tip { font-size: 12.5px; color: #9a9a9a; }
</style>
