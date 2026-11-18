<template>
  <div>
    <!-- ============ 个人看板（学生未加组 / 教师未建组） ============ -->
    <template v-if="scope === 'SELF'">
      <div class="self-banner">
        <span class="banner-hi">{{ store.displayName }}，欢迎回来 👋</span>
        <span class="banner-sub">{{ store.isTeacher ? '创建课题组后可查看名下课题组统计' : '加入课题组后可查看本组统计；当前为个人统计视图' }}</span>
      </div>

      <div class="stat-row self-row">
        <div v-for="card in selfCards" :key="card.key" class="stat-card"
             :style="{ borderTop: `3px solid ${card.color}` }" role="button" tabindex="0"
             @click="card.to && $router.push(card.to)" @keyup.enter="card.to && $router.push(card.to)">
          <!-- 待办角标（如待审批数量 > 0 时提醒，含候补自动转正） -->
          <span v-if="card.alert && Number(card.badge ?? card.value) > 0" class="card-badge">
            {{ Number(card.badge ?? card.value) > 99 ? '99+' : (card.badge ?? card.value) }}
          </span>
          <div class="stat-value" :style="{ color: card.deep }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-arrow">›</div>
          <div class="stat-hint">{{ card.hint }}</div>
        </div>
      </div>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">我的预约趋势 <span class="chart-sub">近 6 个月 · 仅本人</span></div>
            <div ref="selfTrendRef" style="height: 290px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">我的预约状态分布 <span class="chart-sub">Status</span></div>
            <div ref="selfStatusRef" style="height: 290px"></div>
          </div>
        </el-col>
      </el-row>

      <div class="page-card" style="margin-top: 16px">
        <div class="page-title">我的最近预约</div>
        <el-table :data="recent" size="default">
          <el-table-column prop="equipmentName" label="设备" min-width="160" />
          <el-table-column label="借用时间" min-width="300">
            <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 课题组卡片：教师创建 / 学生凭邀请码加入 -->
      <group-card style="margin-top: 16px" @changed="reload" />
    </template>

    <!-- ============ 学生：个人 + 本组 ============ -->
    <template v-else-if="scope === 'SELF_GROUP'">
      <div class="self-banner">
        <span class="banner-hi">{{ store.displayName }}，欢迎回来 👋</span>
        <span class="banner-sub">已加入 {{ data?.myGroups?.length || 0 }} 个课题组 —— 个人统计 + 本组联合统计</span>
      </div>

      <div class="group-tag-row">
        <template v-for="(g, i) in data?.myGroups || []" :key="g.id">
          <el-tag type="success" effect="plain" size="large">📌 {{ g.name }}</el-tag>
          <span v-if="i < (data?.myGroups?.length || 0) - 1" class="group-sep">·</span>
        </template>
        <span class="group-meta">
          负责人：{{ (data?.myGroups || []).map(g => g.ownerName).join('、') }} · 去重成员 {{ data?.groupMemberCount ?? 0 }} 人
        </span>
      </div>

      <div class="section-title">👤 个人统计</div>
      <div class="stat-row self-row">
        <div v-for="card in selfCards" :key="card.key" class="stat-card"
             :style="{ borderTop: `3px solid ${card.color}` }" role="button" tabindex="0"
             @click="card.to && $router.push(card.to)" @keyup.enter="card.to && $router.push(card.to)">
          <!-- 待办角标（如待审批数量 > 0 时提醒，含候补自动转正） -->
          <span v-if="card.alert && Number(card.badge ?? card.value) > 0" class="card-badge">
            {{ Number(card.badge ?? card.value) > 99 ? '99+' : (card.badge ?? card.value) }}
          </span>
          <div class="stat-value" :style="{ color: card.deep }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-arrow">›</div>
          <div class="stat-hint">{{ card.hint }}</div>
        </div>
      </div>

      <div class="section-title">👥 本组统计 <span class="section-sub">同课题组成员的预约数据（仅共享预约信息）</span></div>
      <div class="stat-row self-row">
        <div v-for="card in studentGroupCards" :key="card.key" class="stat-card"
             :style="{ borderTop: `3px solid ${card.color}` }" role="button" tabindex="0"
             @click="card.to && $router.push(card.to)" @keyup.enter="card.to && $router.push(card.to)">
          <div class="stat-value" :style="{ color: card.deep }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-arrow">›</div>
          <div class="stat-hint">{{ card.hint }}</div>
        </div>
      </div>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">我的预约趋势 <span class="chart-sub">近 6 个月 · 仅本人</span></div>
            <div ref="selfTrendRef" style="height: 280px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">本组预约趋势 <span class="chart-sub">近 6 个月 · 本组</span></div>
            <div ref="groupTrendRef" style="height: 280px"></div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">本组预约状态分布 <span class="chart-sub">Status</span></div>
            <div ref="groupStatusRef" style="height: 280px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">本组成员预约 TOP5 <span class="chart-sub">Top Members</span></div>
            <div ref="groupTopRef" style="height: 280px"></div>
          </div>
        </el-col>
      </el-row>

      <div class="page-card" style="margin-top: 16px">
        <div class="page-title">本组最近预约</div>
        <el-table :data="data?.groupRecent || []" size="default">
          <el-table-column prop="username" label="预约人" width="100" />
          <el-table-column prop="equipmentName" label="设备" min-width="150" />
          <el-table-column label="借用时间" min-width="290">
            <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <group-card style="margin-top: 16px" :my-groups="data?.myGroups" @changed="reload" />
    </template>

    <!-- ============ 教师：全局 / 名下课题组 切换 ============ -->
    <template v-else-if="scope === 'TEACHER_GROUPS'">
      <div class="self-banner">
        <span class="banner-hi">{{ store.displayName }} 老师，您好 👋</span>
        <span class="banner-sub">您名下有 {{ data?.myGroups?.length || 0 }} 个课题组，可切换查看全局或课题组统计</span>
      </div>

      <div class="teacher-toolbar">
        <el-radio-group v-model="teacherView" @change="renderCharts">
          <el-radio-button value="global">🌐 全局实验室统计</el-radio-button>
          <el-radio-button value="group">👥 我名下课题组统计</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 全局视图 -->
      <template v-if="teacherView === 'global'">
        <div class="stat-row">
          <div v-for="card in visibleCards" :key="card.key" class="stat-card"
               :style="{ borderTop: `3px solid ${card.color}` }"
               role="button" tabindex="0" :title="`前往${card.toTitle}`"
               @click="go(card)" @keyup.enter="go(card)">
            <!-- 告警类卡片右上角消息提醒角标（数量 > 0 时显示；badge 优先于卡片数值） -->
            <span v-if="card.alert && Number(card.badge ?? card.value) > 0" class="card-badge">
              {{ Number(card.badge ?? card.value) > 99 ? '99+' : (card.badge ?? card.value) }}
            </span>
            <div class="stat-value" :style="{ color: card.deep }">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-arrow">›</div>
            <div class="stat-hint">点击查看明细</div>
          </div>
        </div>

        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :span="12">
            <div class="page-card">
              <div class="page-title">设备分类分布</div>
              <div ref="pieRef" style="height: 300px"></div>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="page-card">
              <div class="page-title">近 6 个月预约趋势</div>
              <div ref="barRef" style="height: 300px"></div>
            </div>
          </el-col>
        </el-row>

        <div class="page-card" style="margin-top: 16px">
          <div class="page-title">最新预约</div>
          <el-table :data="recent" size="default">
            <el-table-column prop="username" label="预约人" width="120" />
            <el-table-column prop="equipmentName" label="设备" min-width="160" />
            <el-table-column label="借用时间" min-width="300">
              <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <!-- 课题组视图 -->
      <template v-else>
        <div class="group-pick">
          <span class="group-pick-label">选择课题组：</span>
          <el-select v-model="selectedGroupId" style="width: 280px" @change="renderCharts">
            <el-option v-for="g in data?.myGroups || []" :key="g.id" :value="g.id"
                       :label="`${g.name}（${g.memberCount} 人）`" />
          </el-select>
        </div>
        <template v-if="selectedGroup">
          <div class="stat-row self-row">
            <div v-for="card in teacherGroupCards" :key="card.key" class="stat-card"
                 :style="{ borderTop: `3px solid ${card.color}` }" role="button" tabindex="0"
                 @click="card.to && $router.push(card.to)" @keyup.enter="card.to && $router.push(card.to)">
              <div class="stat-value" :style="{ color: card.deep }">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-arrow">›</div>
              <div class="stat-hint">{{ card.hint }}</div>
            </div>
          </div>

          <el-row :gutter="16" style="margin-top: 16px">
            <el-col :span="12">
              <div class="page-card">
                <div class="page-title">课题组预约趋势 <span class="chart-sub">近 6 个月 · {{ selectedGroup.name }}</span></div>
                <div ref="tgTrendRef" style="height: 280px"></div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="page-card">
                <div class="page-title">成员预约 TOP5 <span class="chart-sub">Top Members</span></div>
                <div ref="tgTopRef" style="height: 280px"></div>
              </div>
            </el-col>
          </el-row>
          <el-row :gutter="16" style="margin-top: 16px">
            <el-col :span="12">
              <div class="page-card">
                <div class="page-title">课题组预约状态分布 <span class="chart-sub">Status</span></div>
                <div ref="tgStatusRef" style="height: 280px"></div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="page-card">
                <div class="page-title">课题组最近预约</div>
                <el-table :data="selectedGroup.recent || []" size="default">
                  <el-table-column prop="username" label="预约人" width="90" />
                  <el-table-column prop="equipmentName" label="设备" min-width="130" />
                  <el-table-column label="状态" width="92">
                    <template #default="{ row }">
                      <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-col>
          </el-row>
        </template>
        <el-empty v-else description="暂无课题组，请在下方创建" />
      </template>

      <!-- 教师课题组管理（myGroups = 名下课题组） -->
      <group-card style="margin-top: 16px" :my-groups-owned="data?.myGroups" @changed="reload" />
    </template>

    <!-- ============ 安全员：全局统计（突出损耗与安全隐患） ============ -->
    <template v-else-if="scope === 'SAFETY'">
      <div class="self-banner safety-banner">
        <span class="banner-hi">🛡 安全核查视图</span>
        <span class="banner-sub">固定展示实验室全局统计，重点突出损耗与安全隐患数据（只读核查，不可审批）</span>
      </div>

      <div class="section-title">⚠️ 损耗与安全隐患 <span class="section-sub">重点监控</span></div>
      <div class="stat-row">
        <div v-for="card in safetyCards" :key="card.key" class="stat-card"
             :style="{ borderTop: `3px solid ${card.color}` }"
             role="button" tabindex="0" :title="`前往${card.toTitle}`"
             @click="go(card)" @keyup.enter="go(card)">
          <!-- 告警类卡片右上角消息提醒角标（数量 > 0 时显示；badge 优先于卡片数值） -->
          <span v-if="card.alert && Number(card.badge ?? card.value) > 0" class="card-badge">
            {{ Number(card.badge ?? card.value) > 99 ? '99+' : (card.badge ?? card.value) }}
          </span>
          <div class="stat-value" :style="{ color: card.deep }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-arrow">›</div>
          <div class="stat-hint">点击查看明细</div>
        </div>
      </div>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">损耗处理状态分布 <span class="chart-sub">Damage Status</span></div>
            <div ref="safetyDamageRef" style="height: 290px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">本月高危设备使用 TOP5 <span class="chart-sub">High-Risk Usage</span></div>
            <div ref="riskRef" style="height: 290px"></div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">设备分类分布</div>
            <div ref="pieRef" style="height: 290px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">近 6 个月预约趋势</div>
            <div ref="barRef" style="height: 290px"></div>
          </div>
        </el-col>
      </el-row>

      <div class="page-card" style="margin-top: 16px">
        <div class="page-title">最新预约 <span class="chart-sub">全部预约 · 安全核查用</span></div>
        <el-table :data="recent" size="default">
          <el-table-column prop="username" label="预约人" width="120" />
          <el-table-column prop="equipmentName" label="设备" min-width="160" />
          <el-table-column label="借用时间" min-width="300">
            <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ============ 管理员 / 设备保管员：全局看板 ============ -->
    <template v-else-if="scope === 'ALL'">
      <div class="stat-row">
        <div v-for="card in visibleCards" :key="card.key" class="stat-card"
             :style="{ borderTop: `3px solid ${card.color}` }"
             role="button" tabindex="0" :title="`前往${card.toTitle}`"
             @click="go(card)" @keyup.enter="go(card)">
          <!-- 告警类卡片右上角消息提醒角标（数量 > 0 时显示；badge 优先于卡片数值） -->
          <span v-if="card.alert && Number(card.badge ?? card.value) > 0" class="card-badge">
            {{ Number(card.badge ?? card.value) > 99 ? '99+' : (card.badge ?? card.value) }}
          </span>
          <div class="stat-value" :style="{ color: card.deep }">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-arrow">›</div>
          <div class="stat-hint">点击查看明细</div>
        </div>
      </div>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">设备分类分布</div>
            <div ref="pieRef" style="height: 300px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">近 6 个月预约趋势</div>
            <div ref="barRef" style="height: 300px"></div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">本月高危设备使用 TOP5 <span class="chart-sub">High-Risk Usage</span></div>
            <div ref="riskRef" style="height: 280px"></div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <div class="page-title">候补预约统计 <span class="chart-sub">Waitlist Stats</span></div>
            <div ref="waitRef" style="height: 280px"></div>
          </div>
        </el-col>
      </el-row>

      <div class="page-card" style="margin-top: 16px">
        <div class="page-title">最新预约</div>
        <el-table :data="recent" size="default">
          <el-table-column prop="username" label="预约人" width="120" />
          <el-table-column prop="equipmentName" label="设备" min-width="160" />
          <el-table-column label="借用时间" min-width="300">
            <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ RESERVATION_STATUS[row.status] }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 导出：按角色收敛（后端强制） -->
    <div class="toolbar" style="margin-top: 16px; margin-bottom: 0" v-if="scope">
      <div style="flex: 1"></div>
      <el-button :loading="exporting" @click="exportExcel">
        {{ exportLabel }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { statsDashboard, exportReservations, saveBlob, reservationCancelStats, RESERVATION_STATUS } from '../api'
import { useUserStore } from '../store/user'
import GroupCard from '../components/GroupCard.vue'

const router = useRouter()
const store = useUserStore()

const data = ref(null)
const scope = computed(() => data.value?.scope || null)
const teacherView = ref('global')
const selectedGroupId = ref(null)
const selectedGroup = computed(() =>
  (data.value?.myGroups || []).find(g => g.id === selectedGroupId.value) || null)
const recent = computed(() => {
  const d = data.value
  if (!d) return []
  if (d.scope === 'SELF') return d.myRecent || []
  return d.recentReservations || []
})

/* ---------- 30 天取消统计与封禁状态（看板卡片展示） ---------- */
const cancelStats = ref({ count30d: 0, limit: 5, banned: false, remainingDays: 0 })

async function reload() {
  data.value = await statsDashboard()
  // 30 天取消统计与封禁状态（所有角色均可自主预约，统一展示）
  reservationCancelStats().then(s => { cancelStats.value = s }).catch(() => {})
  if (scope.value === 'TEACHER_GROUPS') {
    if (!data.value.myGroups?.some(g => g.id === selectedGroupId.value)) {
      selectedGroupId.value = data.value.myGroups?.[0]?.id ?? null
    }
  }
  await nextTick()
  renderCharts()
}

/* ---------- 学生个人卡片 ---------- */
const selfCards = computed(() => {
  const d = data.value || {}
  const cs = cancelStats.value
  const pending = Number(d.myPendingCount ?? 0)
  return [
    { key: 'myTotal', label: '本人预约次数', value: d.myReservationTotal ?? '-', color: '#8fa9bc', deep: '#5f7f95',
      to: '/my-reservations', hint: pending > 0 ? `有 ${pending} 条预约待审批（含候补转正）` : '查看我的预约',
      alert: pending > 0, badge: pending },
    { key: 'credit', label: '信用积分', value: d.creditScore ?? '-', color: '#9fb8ae', deep: '#6f9487',
      to: '/my-reservations', hint: '违约 / 过期会扣除信用分' },
    { key: 'cancel30d', label: '近 30 天取消', value: cs.count30d ?? 0,
      color: cs.banned ? '#bc9494' : '#c7bdb4', deep: cs.banned ? '#a06a6a' : '#948677',
      to: '/my-reservations',
      hint: cs.banned ? `已超限 · 剩余解禁 ${cs.remainingDays} 天` : `上限 ${cs.limit} 次 · 超限暂停预约` },
    { key: 'myDamage', label: '本人上报损耗', value: d.myDamageTotal ?? '-', color: '#bc9494', deep: '#a06a6a',
      to: '/damage', hint: '查看我的报修记录' }
  ]
})

/* ---------- 学生：本组卡片（可同时多个课题组，统计为联合去重口径） ---------- */
const studentGroupCards = computed(() => {
  const d = data.value || {}
  const groupNames = (d.myGroups || []).map(g => g.name).join('、')
  return [
    { key: 'gTotal', label: '本组预约总数', value: d.groupReservationTotal ?? '-', color: '#8fa9bc', deep: '#5f7f95',
      to: '/calendar', hint: groupNames ? `含：${groupNames}` : '日历查看本组预约' },
    { key: 'gMonth', label: '本组本月预约', value: d.groupReservationMonth ?? '-', color: '#c7bdb4', deep: '#948677',
      to: '/calendar', query: { month: dayjs().format('YYYY-MM') }, hint: '本月预约明细' },
    { key: 'gMembers', label: '本组成员数', value: d.groupMemberCount ?? '-', color: '#9fb8ae', deep: '#6f9487',
      to: null, hint: '已加入课题组去重成员' }
  ]
})

/* ---------- 教师：所选课题组卡片（无待审批卡片，教师无审批权限） ---------- */
const teacherGroupCards = computed(() => {
  const g = selectedGroup.value || {}
  return [
    { key: 'gTotal', label: '课题组预约总数', value: g.reservationTotal ?? '-', color: '#8fa9bc', deep: '#5f7f95',
      to: '/calendar', hint: '日历查看本组预约' },
    { key: 'gMonth', label: '本月预约', value: g.reservationMonth ?? '-', color: '#c7bdb4', deep: '#948677',
      to: '/calendar', query: { month: dayjs().format('YYYY-MM') }, hint: '本月预约明细' },
    { key: 'gMembers', label: '成员数', value: g.memberCount ?? '-', color: '#9fb8ae', deep: '#6f9487',
      to: null, hint: '课题组成员' },
    { key: 'myTotal', label: '我的预约次数', value: data.value?.myReservationTotal ?? '-', color: '#c9b4a0', deep: '#9c8168',
      to: '/my-reservations', hint: '查看我的预约' }
  ]
})

/* ---------- 全局卡片（管理员 / 保管员 / 教师全局视图） ---------- */
const cards = computed(() => {
  const d = data.value
  return [
    { key: 'total', label: '设备总数', value: d?.equipmentTotal ?? '-', color: '#8fa9bc', deep: '#5f7f95',
      to: '/equipment', query: {}, toTitle: '设备名录' },
    { key: 'available', label: '可用设备', value: d?.equipmentAvailable ?? '-', color: '#9fb8ae', deep: '#6f9487',
      to: '/equipment', query: { status: 'AVAILABLE' }, toTitle: '设备名录（空闲设备）' },
    { key: 'repairing', label: '维修中设备', value: d?.equipmentRepairing ?? '-', color: '#c9b4a0', deep: '#9c8168',
      to: '/equipment', query: { status: 'UNDER_REPAIR' }, toTitle: '设备名录（维修中设备）' },
    { key: 'pending', label: '待审批', value: d?.pendingApprovals ?? '-', color: '#a9a6b8', deep: '#7d7992',
      to: '/approval', query: { status: 'PENDING' }, toTitle: '借用审批', approverOnly: true, alert: true },
    { key: 'month', label: '本月预约', value: d?.monthReservations ?? '-', color: '#c7bdb4', deep: '#948677',
      to: '/calendar', query: { month: dayjs().format('YYYY-MM') }, toTitle: '预约日历（本月）' },
    { key: 'damage', label: '待处理损耗', value: d?.damageReported ?? '-', color: '#bc9494', deep: '#a06a6a',
      to: '/damage', query: { status: 'REPORTED' }, toTitle: '损耗记录（待处理）', managerOnly: true, alert: true },
    { key: 'highRisk', label: '本月高危使用', value: d?.highRiskMonth ?? '-', color: '#d0a8a8', deep: '#a05252',
      to: '/safety', query: {}, toTitle: '安全中心（高危使用记录）', safetyOnly: true },
    { key: 'waitlist', label: '候补排队中', value: d?.waitlistWaiting ?? '-', color: '#a8c0bb', deep: '#5f8378',
      to: '/my-reservations', query: { tab: 'waitlist' }, toTitle: '我的预约（候补队列）', managerOnly: true }
  ]
})

/**
 * 按当前登录角色过滤可见卡片：
 * - 待审批仅审批角色（管理员 / 保管员）；
 * - 高危卡片仅安全相关角色（安全员 / 管理员）；
 * - 待处理损耗 / 候补排队跳转的管理页面仅管理员与保管员可见（教师跳转后只能看到本人数据，口径不一致故隐藏）。
 */
const visibleCards = computed(() =>
  cards.value.filter(card =>
    (!card.approverOnly || store.canApprove) &&
    (!card.safetyOnly || store.isSafety) &&
    (!card.managerOnly || store.canManage)))

/* ---------- 安全员：损耗与隐患重点卡片 ---------- */
const safetyCards = computed(() => {
  const d = data.value || {}
  return [
    { key: 'dReported', label: '待处理损耗', value: d.damageReported ?? '-', color: '#bc9494', deep: '#a06a6a',
      to: '/damage', query: { status: 'REPORTED' }, toTitle: '损耗记录（待处理）', alert: true },
    { key: 'dFixing', label: '维修中损耗', value: d.damageFixing ?? '-', color: '#c9b4a0', deep: '#9c8168',
      to: '/damage', query: { status: 'FIXING' }, toTitle: '损耗记录（维修中）' },
    { key: 'dFixed', label: '已修复损耗', value: d.damageFixed ?? '-', color: '#9fb8ae', deep: '#6f9487',
      to: '/damage', query: { status: 'FIXED' }, toTitle: '损耗记录（已修复）' },
    { key: 'hazard', label: '未处理隐患', value: d.hazardPending ?? '-', color: '#d0a8a8', deep: '#a05252',
      to: '/safety', query: {}, toTitle: '安全中心（隐患处理）', alert: true },
    { key: 'highRisk', label: '本月高危使用', value: d.highRiskMonth ?? '-', color: '#d6c3a8', deep: '#a08662',
      to: '/safety', query: {}, toTitle: '安全中心（高危使用记录）' },
    { key: 'repairingEq', label: '维修中设备', value: d.equipmentRepairing ?? '-', color: '#a9a6b8', deep: '#7d7992',
      to: '/equipment', query: { status: 'UNDER_REPAIR' }, toTitle: '设备名录（维修中设备）' },
    { key: 'month', label: '本月预约', value: d.monthReservations ?? '-', color: '#8fa9bc', deep: '#5f7f95',
      to: '/calendar', query: { month: dayjs().format('YYYY-MM') }, toTitle: '预约日历（本月）' }
  ]
})

function go(card) {
  router.push({ path: card.to, query: card.query })
}

const exportLabel = computed(() => {
  if (scope.value === 'SELF') return '导出我的预约记录 Excel'
  if (scope.value === 'SELF_GROUP') return '导出预约记录 Excel（本人 + 本组）'
  if (scope.value === 'TEACHER_GROUPS') return '导出预约记录 Excel（本人 + 名下课题组）'
  if (scope.value === 'SAFETY') return '导出全部预约记录 Excel'
  return '导出预约记录 Excel'
})

/** 导出预约记录：学生→本人+本组，教师→本人+名下组，管理员/保管员/安全员→全部（后端按角色强制收敛） */
const exporting = ref(false)
async function exportExcel() {
  exporting.value = true
  try {
    const mine = scope.value === 'SELF' || scope.value === 'SELF_GROUP'
    const blob = await exportReservations({ mine })
    saveBlob(blob, `预约记录表_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
  } finally {
    exporting.value = false
  }
}

/** 莫兰迪蓝实验室图表色板 */
const MORANDI = ['#8fa9bc', '#9fb8ae', '#c9b4a0', '#a9a6b8', '#bc9494', '#c7bdb4', '#b0c3d0', '#8fb0a5']
const AXIS_COLOR = '#5c666d'
const SPLIT_COLOR = '#e8eef1'

const pieRef = ref()
const barRef = ref()
const riskRef = ref()
const waitRef = ref()
const selfTrendRef = ref()
const selfStatusRef = ref()
const groupTrendRef = ref()
const groupTopRef = ref()
const groupStatusRef = ref()
const tgTrendRef = ref()
const tgTopRef = ref()
const tgStatusRef = ref()
const safetyDamageRef = ref()
let charts = []

const fmt = t => (t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-')
const statusType = s =>
  ({ PENDING: 'warning', APPROVED: 'primary', CLAIMED: 'success', REJECTED: 'danger', RETURNED: 'info',
     CANCELLED: 'info', AUTO_RELEASED: 'danger', EXPIRED: 'info' }[s] || 'info')

/* ---------- 图表通用配置 ---------- */
const tooltip = {
  trigger: 'item',
  backgroundColor: 'rgba(255, 255, 255, 0.97)',
  borderColor: '#e6edf0',
  textStyle: { color: '#333333', fontSize: 13 }
}
const axisTooltip = {
  trigger: 'axis',
  backgroundColor: 'rgba(255, 255, 255, 0.97)',
  borderColor: '#e6edf0',
  textStyle: { color: '#333333', fontSize: 13 },
  axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(143, 169, 188, 0.12)' } }
}
function trendOption(trend) {
  return {
    animation: false, ...axisTooltip,
    grid: { left: 8, right: 16, top: 18, bottom: 6, containLabel: true },
    xAxis: {
      type: 'category',
      data: trend.map(i => i.month),
      axisLine: { lineStyle: { color: SPLIT_COLOR } },
      axisTick: { show: false },
      axisLabel: { color: AXIS_COLOR, fontSize: 13, fontWeight: 500 }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: AXIS_COLOR, fontSize: 13, fontWeight: 500 },
      splitLine: { lineStyle: { color: SPLIT_COLOR, type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      data: trend.map(i => i.count),
      barWidth: '42%',
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#8fa9bc' },
          { offset: 1, color: '#dce8ef' }
        ])
      }
    }]
  }
}
function statusPieOption(rows) {
  return {
    animation: false, color: MORANDI,
    tooltip,
    legend: { bottom: 0, type: 'scroll', itemWidth: 10, itemHeight: 10, icon: 'circle',
              textStyle: { color: '#333333', fontSize: 13, fontWeight: 500 } },
    series: [{
      type: 'pie', radius: ['38%', '64%'], center: ['50%', '44%'],
      itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
      data: rows || [],
      label: { formatter: '{b}: {c}', color: '#333333', fontSize: 13, fontWeight: 500 },
      labelLine: { lineStyle: { color: '#d5dee3' } }
    }]
  }
}
function topBarOption(rows, colorFrom, colorTo, horizontal = true) {
  const names = rows.map(i => i.name || i.equipment_name || '未知')
  const values = rows.map(i => i.value)
  const gradient = horizontal
    ? new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: colorFrom }, { offset: 1, color: colorTo }])
    : new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: colorTo }, { offset: 1, color: colorFrom }])
  const base = { animation: false, ...axisTooltip }
  if (horizontal) {
    return {
      ...base,
      grid: { left: 8, right: 30, top: 14, bottom: 6, containLabel: true },
      xAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false },
               axisLabel: { color: AXIS_COLOR, fontSize: 13, fontWeight: 500 },
               splitLine: { lineStyle: { color: SPLIT_COLOR, type: 'dashed' } } },
      yAxis: { type: 'category', data: names, axisLine: { lineStyle: { color: SPLIT_COLOR } }, axisTick: { show: false },
               axisLabel: { color: AXIS_COLOR, fontSize: 13, fontWeight: 500 } },
      series: [{ type: 'bar', data: values, barWidth: '46%', itemStyle: { borderRadius: [0, 6, 6, 0], color: gradient } }]
    }
  }
  return {
    ...base,
    grid: { left: 8, right: 16, top: 18, bottom: 6, containLabel: true },
    xAxis: { type: 'category', data: names, axisLine: { lineStyle: { color: SPLIT_COLOR } }, axisTick: { show: false },
             axisLabel: { color: AXIS_COLOR, fontSize: 13, fontWeight: 500 } },
    yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false },
             axisLabel: { color: AXIS_COLOR, fontSize: 13, fontWeight: 500 },
             splitLine: { lineStyle: { color: SPLIT_COLOR, type: 'dashed' } } },
    series: [{ type: 'bar', data: values, barWidth: '42%', borderRadius: [6, 6, 0, 0], itemStyle: { color: gradient } }]
  }
}

/** 统一渲染当前作用域图表（切换教师视图 / 重新加载时复用） */
function renderCharts() {
  charts.forEach(c => c.dispose())
  charts = []
  const d = data.value
  if (!d) return

  if (d.scope === 'SELF' || d.scope === 'SELF_GROUP') {
    const trend = echarts.init(selfTrendRef.value)
    trend.setOption(trendOption(d.myTrend || []))
    charts.push(trend)
    if (d.scope === 'SELF') {
      const pie = echarts.init(selfStatusRef.value)
      pie.setOption(statusPieOption(d.myStatusStats || []))
      charts.push(pie)
    } else {
      const gTrend = echarts.init(groupTrendRef.value)
      gTrend.setOption(trendOption(d.groupTrend || []))
      const gPie = echarts.init(groupStatusRef.value)
      gPie.setOption(statusPieOption(d.groupStatusStats || []))
      const gTop = echarts.init(groupTopRef.value)
      gTop.setOption(topBarOption(d.groupStudentTop || [], '#e0e6ee', '#a9a6b8'))
      charts.push(gTrend, gPie, gTop)
    }
    return
  }

  if (d.scope === 'TEACHER_GROUPS') {
    if (teacherView.value === 'global') {
      renderCategoryPie()
      const bar = echarts.init(barRef.value)
      bar.setOption(trendOption(d.monthTrend || []))
      charts.push(bar)
    } else if (selectedGroup.value) {
      const g = selectedGroup.value
      const trend = echarts.init(tgTrendRef.value)
      trend.setOption(trendOption(g.trend || []))
      const top = echarts.init(tgTopRef.value)
      top.setOption(topBarOption(g.studentTop || [], '#e0e6ee', '#a9a6b8'))
      const pie = echarts.init(tgStatusRef.value)
      pie.setOption(statusPieOption(g.statusStats || []))
      charts.push(trend, top, pie)
    }
    return
  }

  if (d.scope === 'SAFETY') {
    const dmg = echarts.init(safetyDamageRef.value)
    dmg.setOption(statusPieOption(d.damageStatusStats || []))
    const hrTop = (d.highRiskTop || []).slice(0, 5)
    const risk = echarts.init(riskRef.value)
    risk.setOption(topBarOption(hrTop, '#e5cdcd', '#bc9494'))
    renderCategoryPie()
    const bar = echarts.init(barRef.value)
    bar.setOption(trendOption(d.monthTrend || []))
    charts.push(risk, bar)
    return
  }

  // ALL：管理员 / 保管员全局
  renderCategoryPie()

  const bar = echarts.init(barRef.value)
  bar.setOption(trendOption(d.monthTrend || []))

  const hrTop = (d.highRiskTop || []).slice(0, 5)
  const risk = echarts.init(riskRef.value)
  risk.setOption(topBarOption(hrTop, '#e5cdcd', '#bc9494'))

  const wait = echarts.init(waitRef.value)
  const waiting = d.waitlistWaiting ?? 0
  wait.setOption({
    animation: false,
    series: [{
      type: 'gauge',
      startAngle: 210, endAngle: -30,
      min: 0, max: Math.max(waiting * 2, 10), splitNumber: 5,
      radius: '92%', center: ['50%', '58%'],
      axisLine: { lineStyle: { width: 16, color: [[1, '#e8eef1']] } },
      pointer: { itemStyle: { color: '#8fa9bc' }, width: 4 },
      axisTick: { distance: -16, lineStyle: { color: '#ffffff', width: 1 } },
      splitLine: { distance: -16, length: 16, lineStyle: { color: '#ffffff', width: 2 } },
      axisLabel: { color: AXIS_COLOR, fontSize: 12, distance: 22 },
      detail: {
        valueAnimation: false,
        formatter: '{value} 人',
        color: '#5f8378',
        fontSize: 30, fontWeight: 700, offsetCenter: [0, '38%']
      },
      title: { offsetCenter: [0, '66%'], color: '#333333', fontSize: 14, fontWeight: 600 },
      data: [{ value: waiting, name: '当前候补排队人数' }]
    }]
  })

  charts.push(bar, risk, wait)
}

/** 设备分类饼图（全局 / 安全员视图共用） */
function renderCategoryPie() {
  const pie = echarts.init(pieRef.value)
  pie.setOption({
    animation: false, color: MORANDI, tooltip,
    legend: { bottom: 0, type: 'scroll', itemWidth: 10, itemHeight: 10, icon: 'circle',
              textStyle: { color: '#333333', fontSize: 13, fontWeight: 500 } },
    series: [{
      type: 'pie', radius: ['38%', '64%'], center: ['50%', '44%'],
      itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
      data: data.value?.categoryStats || [],
      label: { formatter: '{b}: {c}', color: '#333333', fontSize: 13, fontWeight: 500 },
      labelLine: { lineStyle: { color: '#d5dee3' } }
    }]
  })
  charts.push(pie)
}

onActivated(reload)

function resize() { charts.forEach(c => c.resize()) }
onMounted(() => window.addEventListener('resize', resize))
onBeforeUnmount(() => { window.removeEventListener('resize', resize); charts.forEach(c => c.dispose()) })
</script>

<style scoped>
.chart-sub { font-size: 12.5px; font-weight: 400; color: #777777; letter-spacing: 0.5px; margin-left: 6px; }
/* 等分卡片行 */
.stat-row { display: flex; gap: 16px; }
.self-row .stat-card { flex: 1 1 0; }
/* 个人看板欢迎横幅 */
.self-banner {
  display: flex;
  align-items: baseline;
  gap: 14px;
  background: linear-gradient(135deg, #eef5f8, #e0ebf1);
  border: 1px solid #d5e2ea;
  border-radius: 14px;
  padding: 16px 22px;
}
.safety-banner { background: linear-gradient(135deg, #f8f1ee, #f0e3dd); border-color: #e6d3cb; }
.banner-hi { font-size: 17px; font-weight: 700; color: #2f4a5c; letter-spacing: 0.4px; }
.banner-sub { font-size: 13px; color: #5c7484; }
/* 分区标题 */
.section-title { font-size: 15px; font-weight: 700; color: #3d5566; margin: 20px 0 10px; letter-spacing: 0.4px; }
.section-sub { font-size: 12.5px; font-weight: 400; color: #8a99a3; margin-left: 8px; }
/* 课题组信息行 */
.group-tag-row { display: flex; align-items: center; gap: 12px; margin-top: 14px; }
.group-meta { font-size: 13px; color: #6b7d89; }
/* 教师切换工具条 */
.teacher-toolbar { margin: 16px 0 4px; }
.group-pick { display: flex; align-items: center; gap: 10px; margin: 14px 0 4px; }
.group-pick-label { font-size: 14px; font-weight: 600; color: #3d5566; }
.stat-card {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  background: #ffffff;
  border: 1px solid #eaf0f3;
  border-top-width: 3px;
  border-radius: 14px;
  padding: 18px 16px;
  text-align: center;
  box-shadow: 0 4px 16px rgba(150, 165, 175, 0.08);
  transition: transform 0.22s ease, box-shadow 0.22s ease, background-color 0.22s ease;
  cursor: pointer;
  outline: none;
  user-select: none;
}
.stat-card.no-cursor { cursor: default; }
/* 告警类卡片右上角消息提醒角标（与侧边栏借用审批角标同款莫兰迪浅红） */
.card-badge {
  position: absolute;
  top: -8px;
  right: -6px;
  min-width: 22px;
  height: 22px;
  line-height: 22px;
  padding: 0 7px;
  border-radius: 11px;
  background: linear-gradient(135deg, #c98d8d, #b07272);
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.5px;
  box-shadow: 0 2px 6px rgba(176, 114, 114, 0.38);
  z-index: 2;
}
.stat-card:hover,
.stat-card:focus-visible {
  transform: translateY(-4px);
  box-shadow: 0 10px 26px rgba(150, 165, 175, 0.2);
  background: #fcfdfe;
}
.stat-card:active { transform: translateY(-1px); }
.stat-value { font-size: 30px; font-weight: 750; letter-spacing: 0.6px; line-height: 1.15; }
.stat-label { font-size: 14px; color: #333333; font-weight: 600; margin-top: 8px; letter-spacing: 0.3px; }
.stat-arrow {
  position: absolute;
  right: 12px;
  top: 8px;
  font-size: 15px;
  line-height: 1;
  color: #ccd6dc;
  opacity: 0;
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.stat-card:hover .stat-arrow,
.stat-card:focus-visible .stat-arrow { opacity: 1; transform: translateX(2px); }
/* 可点击提示：hover 时淡入 */
.stat-hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 5px;
  font-size: 11px;
  letter-spacing: 0.4px;
  color: #a8b8c2;
  opacity: 0;
  transition: opacity 0.22s ease;
  pointer-events: none;
}
.stat-card:hover .stat-hint,
.stat-card:focus-visible .stat-hint { opacity: 1; }
</style>
