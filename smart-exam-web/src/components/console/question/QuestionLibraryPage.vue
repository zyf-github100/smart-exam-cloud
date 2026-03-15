<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../../../api/client'
import { useAsyncAction } from '../../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

const typeOptions = [
  { value: '', label: '全部题型' },
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTI', label: '多选题' },
  { value: 'JUDGE', label: '判断题' },
  { value: 'FILL', label: '填空题' },
  { value: 'SHORT', label: '简答题' },
]

const typeLabelMap = Object.fromEntries(
  typeOptions.filter((item) => item.value).map((item) => [item.value, item.label])
)

const filters = reactive({
  keyword: '',
  type: '',
  questionId: '',
})

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const questionList = ref([])
const questionDetail = ref(null)
let listTimer = null

const detailTypeLabel = computed(() =>
  questionDetail.value ? renderQuestionTypeLabel(questionDetail.value.type) : '-'
)

const detailAnswerText = computed(() => {
  if (!questionDetail.value) return '-'
  const raw = String(questionDetail.value.answer || '').trim()
  if (!raw) return '-'

  if (questionDetail.value.type === 'JUDGE') {
    return raw.toLowerCase() === 'true' ? '正确 (true)' : '错误 (false)'
  }
  if (questionDetail.value.type === 'MULTI') {
    return raw.split(/[,，\s]+/).filter(Boolean).join(', ')
  }
  return raw
})

const detailOptions = computed(() => {
  const options = questionDetail.value?.options
  return Array.isArray(options) ? options : []
})

const showOptionPanel = computed(() => ['SINGLE', 'MULTI'].includes(questionDetail.value?.type))

watch(
  () => [filters.keyword, filters.type],
  () => {
    pagination.pageNo = 1
    if (listTimer) {
      clearTimeout(listTimer)
    }
    listTimer = window.setTimeout(() => {
      listQuestions()
    }, 250)
  }
)

const renderQuestionTypeLabel = (type) => typeLabelMap[type] || type || '-'

const formatDateTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString()
}

const listQuestions = async () => {
  const params = {
    keyword: filters.keyword.trim() || undefined,
    type: filters.type || undefined,
    page: pagination.pageNo,
    size: pagination.pageSize,
  }
  const data = await run('list', () => api.listQuestions(params))
  if (!data) {
    return
  }
  questionList.value = Array.isArray(data.records) ? data.records : []
  pagination.total = Number(data.total || 0)
  pagination.pageNo = Number(data.page || pagination.pageNo)
  pagination.pageSize = Number(data.size || pagination.pageSize)
}

const refreshList = async () => {
  pagination.pageNo = 1
  await listQuestions()
}

const getQuestionById = async (questionId) => {
  const targetId = String(questionId || '').trim()
  if (!targetId) {
    ElMessage.warning('请输入题目 ID')
    return
  }
  const data = await run('detail', () => api.getQuestion(targetId))
  if (data) {
    questionDetail.value = data
    filters.questionId = targetId
  }
}

const searchById = async () => {
  await getQuestionById(filters.questionId)
}

const selectQuestion = async (row) => {
  await getQuestionById(row?.id)
}

const handlePageChange = async (pageNo) => {
  pagination.pageNo = pageNo
  await listQuestions()
}

const handlePageSizeChange = async (pageSize) => {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await listQuestions()
}

onBeforeUnmount(() => {
  if (listTimer) {
    clearTimeout(listTimer)
  }
})

onMounted(async () => {
  await listQuestions()
})
</script>

<template>
  <div class="stage-layout two-panels">
    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">题目检索</h3>
          <p class="block-sub">列表改为服务端分页，只返回摘要字段，详情单独按 ID 拉取。</p>
        </div>
      </div>

      <div class="form-grid cols-3">
        <el-input v-model="filters.keyword" placeholder="关键词（ID / 题干 / 知识点）" clearable />
        <el-select v-model="filters.type" placeholder="题型筛选" clearable>
          <el-option
            v-for="item in typeOptions"
            :key="item.value || 'ALL'"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-input v-model="filters.questionId" placeholder="题目 ID（精确）" clearable>
          <template #append>
            <el-button :loading="loading.detail" @click="searchById">查询</el-button>
          </template>
        </el-input>
      </div>

      <div class="action-row list-actions">
        <el-button :loading="loading.list" @click="refreshList">刷新列表</el-button>
        <span class="hint-text">共 {{ pagination.total }} 条，当前第 {{ pagination.pageNo }} 页</span>
      </div>

      <el-table :data="questionList" row-key="id" size="small" max-height="520" @row-click="selectQuestion">
        <el-table-column prop="id" label="ID" min-width="150" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">{{ renderQuestionTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="75" />
        <el-table-column prop="knowledgePoint" label="知识点" min-width="130" show-overflow-tooltip />
        <el-table-column prop="stem" label="题干" min-width="260" show-overflow-tooltip />
      </el-table>

      <el-pagination
        class="pagination-row"
        background
        layout="prev, pager, next, sizes, total"
        :current-page="pagination.pageNo"
        :page-size="pagination.pageSize"
        :page-sizes="[10, 20, 30, 50]"
        :total="pagination.total"
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </section>

    <section class="stack-gap">
      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">题目详情</h3>
        </div>

        <el-empty v-if="!questionDetail" description="请从左侧列表选择题目，查看完整详情。" />

        <div v-else class="detail-card">
          <div class="metrics-grid cols-2">
            <article class="metric-card">
              <span>题目 ID</span>
              <strong>{{ questionDetail.id || '-' }}</strong>
            </article>
            <article class="metric-card">
              <span>题型</span>
              <strong>{{ detailTypeLabel }}</strong>
            </article>
            <article class="metric-card">
              <span>难度</span>
              <strong>{{ questionDetail.difficulty || '-' }}</strong>
            </article>
            <article class="metric-card">
              <span>知识点</span>
              <strong>{{ questionDetail.knowledgePoint || '-' }}</strong>
            </article>
          </div>

          <section class="detail-section">
            <h4>题干</h4>
            <p>{{ questionDetail.stem || '-' }}</p>
          </section>

          <section v-if="showOptionPanel" class="detail-section">
            <h4>选项</h4>
            <ul class="option-list">
              <li v-for="(option, index) in detailOptions" :key="`${option.key || index}`">
                <strong>{{ option.key || '-' }}.</strong>
                <span>{{ option.text || '-' }}</span>
              </li>
            </ul>
          </section>

          <section class="detail-section">
            <h4>标准答案</h4>
            <p>{{ detailAnswerText }}</p>
          </section>

          <section class="detail-section">
            <h4>解析</h4>
            <p>{{ questionDetail.analysis || '暂无解析' }}</p>
          </section>

          <section class="detail-section">
            <h4>创建时间</h4>
            <p>{{ formatDateTime(questionDetail.createdAt) }}</p>
          </section>
        </div>
      </section>
    </section>
  </div>
</template>

<style scoped>
.list-actions {
  margin: 8px 0;
  align-items: center;
}

.pagination-row {
  margin-top: 10px;
  justify-content: flex-end;
}

.detail-card {
  display: grid;
  gap: 10px;
}

.detail-section {
  border: 1px solid rgba(203, 218, 207, 0.9);
  border-radius: 10px;
  background: rgba(250, 254, 250, 0.86);
  padding: 10px;
}

.detail-section h4 {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 800;
  color: #1b4e3a;
}

.detail-section p {
  margin: 0;
  line-height: 1.6;
  color: var(--ink-main);
  white-space: pre-wrap;
}

.option-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
}

.option-list li {
  color: var(--ink-main);
  line-height: 1.5;
}

.option-list strong {
  margin-right: 6px;
}

@media (max-width: 920px) {
  .pagination-row {
    justify-content: flex-start;
  }
}
</style>
