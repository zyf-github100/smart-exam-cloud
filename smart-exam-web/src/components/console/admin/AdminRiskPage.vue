<script setup>
import { computed, inject } from 'vue'
import { prettyJson } from '../../../composables/useAsyncAction'
import { ADMIN_CONSOLE_KEY } from '../../../composables/useAdminConsole'

const admin = inject(ADMIN_CONSOLE_KEY)

const hasRiskDetail = computed(() => Boolean(admin?.sessionRiskDetail?.summary))

const levelTagType = (level) => {
  switch ((level || '').toUpperCase()) {
    case 'CRITICAL':
      return 'danger'
    case 'HIGH':
      return 'warning'
    case 'MEDIUM':
      return 'success'
    default:
      return 'info'
  }
}

const handleRiskPageChange = (value) => {
  admin.riskQuery.page = value
  admin.loadExamRisks()
}
</script>

<template>
  <section v-if="admin" class="console-block">
    <div class="block-head">
      <div>
        <h3 class="block-title">风险监控</h3>
        <p class="block-sub">按考试查看会话风险评分，点选会话查看防作弊事件明细。</p>
      </div>
      <el-button :loading="admin.loading.examRisks" @click="admin.loadExamRisks">刷新风险</el-button>
    </div>

    <div class="form-grid cols-3">
      <el-input v-model="admin.riskQuery.examId" placeholder="考试ID" />
      <el-select v-model="admin.riskQuery.riskLevel" clearable placeholder="风险等级">
        <el-option v-for="level in admin.riskLevelOptions" :key="level" :label="level" :value="level" />
      </el-select>
      <el-input v-model="admin.selectedRiskSessionId" placeholder="会话ID（选填，手动查询）" />
    </div>

    <div class="action-row">
      <el-button type="primary" :loading="admin.loading.examRisks" @click="admin.loadExamRisks">查询风险</el-button>
      <el-button :loading="admin.loading.sessionRisk" @click="admin.loadSessionRisk">查询会话详情</el-button>
    </div>

    <el-table :data="admin.riskPage.records" size="small" max-height="320" @row-click="admin.selectRiskRecord">
      <el-table-column prop="sessionId" label="会话ID" min-width="130" />
      <el-table-column prop="userId" label="用户ID" min-width="110" />
      <el-table-column label="风险等级" width="110">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.riskLevel)">{{ row.riskLevel || 'LOW' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="riskScore" label="风险分" width="95" />
      <el-table-column prop="eventCount" label="事件数" width="90" />
      <el-table-column prop="lastEventType" label="最近事件" min-width="150" />
      <el-table-column prop="lastEventTime" label="最近事件时间" min-width="165" />
    </el-table>

    <el-pagination
      class="pagination-row"
      layout="prev, pager, next, total"
      :total="admin.riskPage.total"
      :page-size="admin.riskQuery.size"
      :current-page="admin.riskQuery.page"
      @current-change="handleRiskPageChange"
    />

    <section v-if="hasRiskDetail" class="risk-detail">
      <h4 class="block-title">会话风险详情：{{ admin.sessionRiskDetail.summary.sessionId }}</h4>
      <div class="metrics-grid cols-3">
        <article class="metric-card">
          <span>风险等级</span>
          <strong>{{ admin.sessionRiskDetail.summary.riskLevel || 'LOW' }}</strong>
        </article>
        <article class="metric-card">
          <span>累计风险分</span>
          <strong>{{ admin.sessionRiskDetail.summary.riskScore || 0 }}</strong>
        </article>
        <article class="metric-card">
          <span>事件总数</span>
          <strong>{{ admin.sessionRiskDetail.summary.eventCount || 0 }}</strong>
        </article>
      </div>

      <el-table :data="admin.sessionRiskDetail.events || []" size="small" max-height="260">
        <el-table-column prop="id" label="事件ID" min-width="130" />
        <el-table-column prop="eventType" label="事件类型" min-width="140" />
        <el-table-column prop="eventScore" label="分值" width="80" />
        <el-table-column prop="eventTime" label="事件时间" min-width="165" />
        <el-table-column prop="clientIp" label="IP" min-width="120" />
      </el-table>

      <pre class="json-block">{{ prettyJson((admin.sessionRiskDetail.events || [])[0]?.metadata || null) }}</pre>
    </section>
  </section>
  <section v-else class="console-block">
    <p class="hint-text">管理上下文初始化失败，请返回“管理员总览”后重试。</p>
  </section>
</template>

<style scoped>
.pagination-row {
  margin-top: 10px;
}

.risk-detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(175, 192, 180, 0.9);
}
</style>

