<script setup>
import { computed, inject, ref, watch } from 'vue'
import { ADMIN_CONSOLE_KEY } from '../../../composables/useAdminConsole'

const admin = inject(ADMIN_CONSOLE_KEY)
const selectedAuditId = ref('')

const auditRecords = computed(() => admin?.auditPage?.records || [])

const formatSimpleValue = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) return value.map((item) => formatSimpleValue(item)).join('、')
  if (typeof value === 'object') {
    const pairs = Object.entries(value).map(([key, item]) => `${key}: ${formatSimpleValue(item)}`)
    return pairs.length ? pairs.join('；') : '-'
  }
  return String(value)
}

const normalizeDetailRows = (detail) => {
  if (detail === null || detail === undefined || detail === '') return []

  let source = detail
  if (typeof detail === 'string') {
    try {
      source = JSON.parse(detail)
    } catch (error) {
      return [{ key: 'detail', value: detail, isList: false, list: [] }]
    }
  }

  if (Array.isArray(source)) {
    return [
      {
        key: 'detail',
        value: '-',
        isList: true,
        list: source.map((item) => formatSimpleValue(item)),
      },
    ]
  }

  if (typeof source !== 'object') {
    return [{ key: 'detail', value: formatSimpleValue(source), isList: false, list: [] }]
  }

  return Object.entries(source).map(([key, value]) => {
    if (Array.isArray(value)) {
      return {
        key,
        value: '-',
        isList: true,
        list: value.map((item) => formatSimpleValue(item)),
      }
    }
    return {
      key,
      value: formatSimpleValue(value),
      isList: false,
      list: [],
    }
  })
}

const selectedAudit = computed(() => {
  const records = auditRecords.value
  if (!records.length) return null
  if (!selectedAuditId.value) return records[0]
  return records.find((item) => String(item.id) === selectedAuditId.value) || records[0]
})

const auditDetailRows = computed(() => normalizeDetailRows(selectedAudit.value?.detail ?? selectedAudit.value?.details ?? null))

watch(
  auditRecords,
  (records) => {
    if (!records.length) {
      selectedAuditId.value = ''
      return
    }
    if (!selectedAuditId.value || !records.some((item) => String(item.id) === selectedAuditId.value)) {
      selectedAuditId.value = String(records[0].id)
    }
  },
  { immediate: true }
)

const handleAuditPageChange = (value) => {
  admin.auditQuery.page = value
  admin.loadAudits()
}

const handleQueryAudits = () => {
  admin.auditQuery.page = 1
  admin.loadAudits()
}

const handleAuditRowClick = (row) => {
  selectedAuditId.value = String(row?.id || '')
}
</script>

<template>
  <section v-if="admin" class="console-block">
    <div class="block-head">
      <div>
        <h3 class="block-title">审计日志检索</h3>
        <p class="block-sub">按操作人、动作、对象和时间窗口查询管理操作记录。</p>
      </div>
      <el-button :loading="admin.loading.audits" @click="admin.loadAudits">刷新日志</el-button>
    </div>

    <div class="form-grid cols-3">
      <el-input v-model="admin.auditQuery.operatorId" placeholder="操作人 ID" />
      <el-input v-model="admin.auditQuery.action" placeholder="动作，例如 USER_STATUS_UPDATED" />
      <el-input v-model="admin.auditQuery.targetType" placeholder="对象类型，例如 SYS_USER" />
    </div>

    <el-date-picker
      v-model="admin.auditRange"
      type="datetimerange"
      range-separator="至"
      start-placeholder="开始时间"
      end-placeholder="结束时间"
      value-format="YYYY-MM-DDTHH:mm:ss"
    />

    <div class="action-row">
      <el-button type="primary" :loading="admin.loading.audits" @click="handleQueryAudits">查询日志</el-button>
    </div>

    <el-table :data="admin.auditPage.records" size="small" max-height="360" @row-click="handleAuditRowClick">
      <el-table-column prop="id" label="日志 ID" min-width="130" />
      <el-table-column prop="operatorId" label="操作人" min-width="100" />
      <el-table-column prop="action" label="动作" min-width="180" />
      <el-table-column prop="targetType" label="对象类型" min-width="120" />
      <el-table-column prop="targetId" label="对象 ID" min-width="120" />
      <el-table-column prop="createdAt" label="时间" min-width="160" />
    </el-table>

    <el-pagination
      class="pagination-row"
      layout="prev, pager, next, total"
      :total="admin.auditPage.total"
      :page-size="admin.auditQuery.size"
      :current-page="admin.auditQuery.page"
      @current-change="handleAuditPageChange"
    />

    <section v-if="selectedAudit" class="audit-detail-panel">
      <h4 class="block-title">日志详情</h4>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="日志 ID">{{ selectedAudit.id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ selectedAudit.operatorId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作角色">{{ selectedAudit.operatorRole || '-' }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ selectedAudit.action || '-' }}</el-descriptions-item>
        <el-descriptions-item label="对象类型">{{ selectedAudit.targetType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="对象 ID">{{ selectedAudit.targetId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ selectedAudit.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table v-if="auditDetailRows.length" :data="auditDetailRows" size="small" class="detail-table">
        <el-table-column prop="key" label="细节项" min-width="160" />
        <el-table-column label="内容" min-width="360">
          <template #default="{ row }">
            <div v-if="row.isList" class="detail-tag-wrap">
              <el-tag
                v-for="(item, index) in row.list"
                :key="`${row.key}-${index}`"
                size="small"
                type="info"
                class="detail-tag"
              >
                {{ item }}
              </el-tag>
            </div>
            <span v-else>{{ row.value }}</span>
          </template>
        </el-table-column>
      </el-table>
      <p v-else class="hint-text">该日志没有额外细节。</p>
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

.audit-detail-panel {
  margin-top: 12px;
}

.detail-table {
  margin-top: 10px;
}

.detail-tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-tag {
  margin: 0;
}
</style>
