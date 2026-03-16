<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AUTH_CHANGED_EVENT, api, getSavedUser } from '../../api/client'
import { hasAnyPermission } from '../../composables/accessControl'
import { usePaperComposerWorkspace } from '../../composables/usePaperComposerWorkspace'
import { invalidateReferenceData } from '../../composables/useReferenceData'
import { useAsyncAction } from '../../composables/useAsyncAction'

const router = useRouter()
const { loading, run } = useAsyncAction()

const {
  composeStep,
  form,
  pendingSelectionCount,
  estimatedScore,
  groupedSelectedQuestions,
  renderTypeLabel,
  getQuestionType,
  getQuestionStem,
  removeQuestion,
  moveQuestion,
  sortByBusinessTypeOrder,
  clearAllQuestions,
  setComposeStep,
  clearQuestionBankState,
  ensureQuestionsCached,
} = usePaperComposerWorkspace()

const activeMode = ref('')
const collapseActiveNames = ref([])
const authVersion = ref(0)

const paperDetailId = ref('')
const paperDetail = ref(null)
const paperQuery = reactive({
  keyword: '',
  page: 1,
  size: 10,
})
const paperPage = ref({
  page: 1,
  size: 10,
  total: 0,
  records: [],
})

const currentUser = computed(() => {
  authVersion.value
  return getSavedUser()
})

const canComposePaper = computed(() => hasAnyPermission(currentUser.value, ['PAPER_CREATE']))
const canQueryPaper = computed(() => hasAnyPermission(currentUser.value, ['PAPER_DETAIL']))
const canBrowseQuestionBank = computed(() =>
  hasAnyPermission(currentUser.value, ['QUESTION_LIST', 'QUESTION_DETAIL'])
)

const availableModes = computed(() => {
  const modes = []
  if (canComposePaper.value) modes.push('compose')
  if (canQueryPaper.value) modes.push('query')
  return modes
})

const modeOptions = computed(() =>
  availableModes.value.map((item) => ({
    value: item,
    label: item === 'compose' ? '组卷模式' : '查卷模式',
  }))
)

const paperDetailQuestions = computed(() => {
  if (!paperDetail.value?.questions) return []
  return [...paperDetail.value.questions].sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0))
})

const paperRecords = computed(() => (Array.isArray(paperPage.value.records) ? paperPage.value.records : []))

watch(
  groupedSelectedQuestions,
  (groups) => {
    collapseActiveNames.value = groups.map((item) => item.type)
  },
  { immediate: true }
)

const syncModeByPermission = () => {
  if (!availableModes.value.length) {
    activeMode.value = ''
    return
  }
  if (!availableModes.value.includes(activeMode.value)) {
    activeMode.value = availableModes.value[0]
  }
}

watch(availableModes, syncModeByPermission, { immediate: true })

watch(activeMode, async (mode) => {
  if (mode === 'query' && canQueryPaper.value && !paperRecords.value.length) {
    await loadPaperList()
  }
})

watch(canQueryPaper, async (enabled) => {
  if (!enabled) {
    paperPage.value = {
      page: 1,
      size: 10,
      total: 0,
      records: [],
    }
    paperDetail.value = null
    paperDetailId.value = ''
    return
  }
  if (activeMode.value === 'query') {
    await loadPaperList()
  }
})

watch(canBrowseQuestionBank, (enabled) => {
  if (!enabled) {
    clearQuestionBankState()
  }
})

const onAuthChanged = () => {
  authVersion.value += 1
}

const normalizeText = (value) => {
  const next = String(value || '').trim()
  return next || undefined
}

const formatDateTimeDisplay = (value) => {
  if (!value) return '-'
  const normalized = String(value).replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const loadPaperList = async () => {
  if (!canQueryPaper.value) {
    paperPage.value = {
      page: 1,
      size: 10,
      total: 0,
      records: [],
    }
    return
  }

  const params = {
    keyword: normalizeText(paperQuery.keyword),
    page: paperQuery.page,
    size: paperQuery.size,
  }

  const data = await run('listPapers', () => api.listPapers(params))
  if (!data) return

  paperPage.value = {
    page: data.page || paperQuery.page,
    size: data.size || paperQuery.size,
    total: data.total || 0,
    records: Array.isArray(data.records) ? data.records : [],
  }
  paperQuery.page = paperPage.value.page
  paperQuery.size = paperPage.value.size
}

const searchPapers = async () => {
  paperQuery.page = 1
  await loadPaperList()
}

const openQuestionPicker = () => {
  if (!canComposePaper.value) {
    ElMessage.warning('当前账号没有创建试卷权限')
    return
  }
  if (!canBrowseQuestionBank.value) {
    ElMessage.warning('当前账号没有题库读取权限，无法从题库选题')
    return
  }
  setComposeStep(1)
  router.push({ name: 'papers-question-picker' })
}

const goNextStep = () => {
  if (composeStep.value === 0) {
    if (!form.name.trim()) {
      ElMessage.warning('请先填写试卷名称')
      return
    }
    if (!form.timeLimitMinutes || Number(form.timeLimitMinutes) < 10) {
      ElMessage.warning('考试时长不能小于 10 分钟')
      return
    }
  }

  if (composeStep.value === 1 && !form.questions.length) {
    ElMessage.warning('请先加入至少一道题目')
    return
  }

  setComposeStep(composeStep.value + 1)
}

const goPrevStep = () => {
  setComposeStep(composeStep.value - 1)
}

const createPaper = async () => {
  if (!canComposePaper.value) {
    ElMessage.warning('当前账号没有创建试卷权限')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入试卷名称')
    return
  }
  if (!form.questions.length) {
    ElMessage.warning('请先从题库中加入至少一道题目')
    return
  }

  sortByBusinessTypeOrder()

  const payload = {
    name: form.name.trim(),
    timeLimitMinutes: Number(form.timeLimitMinutes),
    questions: form.questions.map((item, index) => ({
      questionId: String(item.questionId),
      score: Math.max(1, Number(item.score) || 1),
      orderNo: index + 1,
    })),
  }

  const data = await run('createPaper', () => api.createPaper(payload), { successMessage: '试卷创建成功' })
  if (data) {
    paperDetail.value = data
    paperDetailId.value = data.id
    invalidateReferenceData('papers')
    await loadPaperList()
    activeMode.value = 'query'
  }
}

const getPaper = async (paperId) => {
  if (!canQueryPaper.value) {
    ElMessage.warning('当前账号没有试卷详情权限')
    return
  }
  const targetPaperId = String(paperId || paperDetailId.value || '').trim()
  if (!targetPaperId) {
    ElMessage.warning('请先从列表中选择试卷')
    return
  }

  const data = await run('getPaper', () => api.getPaper(targetPaperId))
  if (data) {
    if (canBrowseQuestionBank.value && Array.isArray(data.questions) && data.questions.length) {
      await ensureQuestionsCached(data.questions.map((item) => item.questionId))
    }
    paperDetail.value = data
    paperDetailId.value = targetPaperId
    activeMode.value = 'query'
  }
}

const selectPaper = async (record) => {
  if (!record?.id) return
  await getPaper(record.id)
}

onMounted(async () => {
  window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
  syncModeByPermission()
  if (canQueryPaper.value) {
    await loadPaperList()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
})
</script>

<template>
  <div class="stack-gap">
    <section class="console-block">
      <div class="block-head mode-head">
        <div>
          <h3 class="block-title">试卷编排工作台</h3>
          <p class="block-sub">基础信息、独立选题和创建确认分段处理，减少单屏拥挤感。</p>
        </div>
        <el-radio-group v-if="modeOptions.length" v-model="activeMode" size="small">
          <el-radio-button v-for="item in modeOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
    </section>

    <section v-if="!availableModes.length" class="console-block">
      <el-empty description="当前账号没有试卷模块可用权限" />
    </section>

    <template v-else-if="activeMode === 'compose'">
      <section class="console-block">
        <el-steps :active="composeStep" finish-status="success" simple>
          <el-step title="基础信息" />
          <el-step title="选择题目" />
          <el-step title="确认创建" />
        </el-steps>
      </section>

      <section v-if="composeStep === 0" class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">第一步：填写基础信息</h3>
        </div>

        <div class="metrics-grid cols-3">
          <article class="metric-card">
            <span>已选题目</span>
            <strong>{{ form.questions.length }}</strong>
          </article>
          <article class="metric-card">
            <span>预计总分</span>
            <strong>{{ estimatedScore }}</strong>
          </article>
          <article class="metric-card">
            <span>考试时长</span>
            <strong>{{ form.timeLimitMinutes }} 分钟</strong>
          </article>
        </div>

        <el-form label-position="top">
          <div class="form-grid cols-2">
            <el-form-item label="试卷名称">
              <el-input v-model="form.name" placeholder="例如：Java 基础测试 A 卷" />
            </el-form-item>
            <el-form-item label="时长（分钟）">
              <el-input-number v-model="form.timeLimitMinutes" :min="10" :step="10" />
            </el-form-item>
          </div>
        </el-form>

        <div class="action-row">
          <el-button type="primary" @click="goNextStep">下一步：选择题目</el-button>
        </div>
      </section>

      <section v-else-if="composeStep === 1" class="console-block">
        <div class="block-head">
          <div>
            <h3 class="block-title">第二步：按题型选择题目</h3>
            <p class="block-sub">选题已拆成独立页面，返回后会保留暂存结果与已加入题目。</p>
          </div>
          <div class="action-row">
            <el-button :disabled="!canBrowseQuestionBank" @click="openQuestionPicker">进入独立选题页</el-button>
            <el-button @click="sortByBusinessTypeOrder">按题型规则自动排序</el-button>
            <el-button type="danger" plain @click="clearAllQuestions">清空题目</el-button>
          </div>
        </div>

        <div class="metrics-grid cols-3 compact-metrics">
          <article class="metric-card">
            <span>已加入试卷</span>
            <strong>{{ form.questions.length }} 题</strong>
          </article>
          <article class="metric-card">
            <span>暂存待加入</span>
            <strong>{{ pendingSelectionCount }} 题</strong>
          </article>
          <article class="metric-card">
            <span>预计总分</span>
            <strong>{{ estimatedScore }} 分</strong>
          </article>
        </div>

        <el-empty v-if="!form.questions.length" description="尚未选题，请点击“进入独立选题页”" />

        <el-collapse v-else v-model="collapseActiveNames">
          <el-collapse-item v-for="group in groupedSelectedQuestions" :key="group.type" :name="group.type">
            <template #title>
              <div class="group-head">
                <span class="group-title">{{ group.label }}</span>
                <span class="group-meta">共 {{ group.count }} 题 · 小计 {{ group.totalScore }} 分</span>
              </div>
            </template>

            <div class="adaptive-table-wrap">
              <el-table :data="group.items" size="small" max-height="260" class="adaptive-table">
                <el-table-column prop="orderNo" label="顺序" width="70" />
                <el-table-column prop="questionId" label="题目 ID" min-width="150" />
                <el-table-column label="题干" min-width="260" show-overflow-tooltip>
                  <template #default="{ row }">{{ getQuestionStem(row.questionId, row.stem) }}</template>
                </el-table-column>
                <el-table-column label="分值" width="120">
                  <template #default="{ row }">
                    <el-input-number v-model="row.score" :min="1" :step="1" class="score-input" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                  <template #default="{ row }">
                    <div class="row-actions">
                      <el-button text @click="moveQuestion(form.questions.indexOf(row), -1)">上移</el-button>
                      <el-button text @click="moveQuestion(form.questions.indexOf(row), 1)">下移</el-button>
                      <el-button text type="danger" @click="removeQuestion(form.questions.indexOf(row))">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-collapse-item>
        </el-collapse>

        <div class="action-row">
          <el-button @click="goPrevStep">上一步</el-button>
          <el-button type="primary" @click="goNextStep">下一步：确认创建</el-button>
        </div>
      </section>

      <section v-else class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">第三步：确认并创建</h3>
        </div>

        <div class="metrics-grid cols-3">
          <article class="metric-card">
            <span>试卷名称</span>
            <strong>{{ form.name || '-' }}</strong>
          </article>
          <article class="metric-card">
            <span>题目总数</span>
            <strong>{{ form.questions.length }}</strong>
          </article>
          <article class="metric-card">
            <span>总分 / 时长</span>
            <strong>总分 {{ estimatedScore }} 分，时长 {{ form.timeLimitMinutes }} 分钟</strong>
          </article>
        </div>

        <div class="adaptive-table-wrap">
          <el-table :data="form.questions" size="small" max-height="360" class="adaptive-table">
            <el-table-column prop="orderNo" label="顺序" width="70" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ renderTypeLabel(getQuestionType(row.questionId, row.type)) }}</template>
            </el-table-column>
            <el-table-column prop="questionId" label="题目 ID" min-width="150" />
            <el-table-column label="题干" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ getQuestionStem(row.questionId, row.stem) }}</template>
            </el-table-column>
            <el-table-column prop="score" label="分值" width="90" />
          </el-table>
        </div>

        <div class="action-row">
          <el-button @click="goPrevStep">上一步</el-button>
          <el-button type="primary" :loading="loading.createPaper" @click="createPaper">创建试卷</el-button>
        </div>
      </section>
    </template>

    <section v-else class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">试卷详情查询</h3>
      </div>

      <div class="query-row">
        <el-input
          v-model="paperQuery.keyword"
          clearable
          placeholder="输入试卷名称关键词检索"
          @keyup.enter="searchPapers"
        />
        <el-button :loading="loading.listPapers" @click="searchPapers">查询</el-button>
        <el-button :loading="loading.listPapers" @click="loadPaperList">刷新</el-button>
      </div>

      <el-empty v-if="!paperRecords.length" description="暂无可查看试卷，请先创建或调整检索关键词" />

      <template v-else>
        <div class="adaptive-table-wrap">
          <el-table :data="paperRecords" size="small" max-height="280" class="adaptive-table">
            <el-table-column prop="id" label="试卷 ID" min-width="150" />
            <el-table-column prop="name" label="试卷名称" min-width="220" show-overflow-tooltip />
            <el-table-column label="总分 / 时长" width="160">
              <template #default="{ row }">总分 {{ row.totalScore || 0 }} 分，时长 {{ row.timeLimitMinutes || 0 }} 分钟</template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ formatDateTimeDisplay(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button link type="primary" :loading="loading.getPaper" @click="selectPaper(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-pagination
          class="pagination-row"
          background
          layout="prev, pager, next, sizes, total"
          :current-page="paperQuery.page"
          :page-size="paperQuery.size"
          :page-sizes="[10, 20, 50]"
          :total="paperPage.total"
          @update:current-page="(val) => ((paperQuery.page = val), loadPaperList())"
          @update:page-size="(val) => ((paperQuery.size = val), (paperQuery.page = 1), loadPaperList())"
        />
      </template>

      <el-empty v-if="!paperDetail" description="请从上方列表选择试卷查看详情" />

      <div v-else class="stack-gap">
        <div class="metrics-grid cols-3">
          <article class="metric-card">
            <span>试卷 ID</span>
            <strong>{{ paperDetail.id || '-' }}</strong>
          </article>
          <article class="metric-card">
            <span>名称</span>
            <strong>{{ paperDetail.name || '-' }}</strong>
          </article>
          <article class="metric-card">
            <span>总分 / 时长</span>
            <strong>总分 {{ paperDetail.totalScore || 0 }} 分，时长 {{ paperDetail.timeLimitMinutes || 0 }} 分钟</strong>
          </article>
        </div>

        <div class="adaptive-table-wrap">
          <el-table :data="paperDetailQuestions" size="small" max-height="420" class="adaptive-table">
            <el-table-column prop="orderNo" label="顺序" width="70" />
            <el-table-column prop="questionId" label="题目 ID" min-width="150" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ renderTypeLabel(getQuestionType(row.questionId)) }}</template>
            </el-table-column>
            <el-table-column prop="score" label="分值" width="90" />
          </el-table>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.mode-head {
  align-items: center;
}

.compact-metrics {
  margin-bottom: 10px;
}

.group-head {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.group-title {
  font-weight: 700;
  color: var(--ink-main);
}

.group-meta {
  font-size: 12px;
  color: var(--ink-soft);
}

.row-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  white-space: nowrap;
}

.pagination-row {
  margin-top: 8px;
  justify-content: flex-end;
}

.adaptive-table-wrap {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 2px;
  -webkit-overflow-scrolling: touch;
}

.adaptive-table-wrap :deep(.adaptive-table) {
  min-width: 920px;
}

.score-input {
  width: 100%;
}

.score-input :deep(.el-input__wrapper) {
  min-width: 0;
}

@media (max-width: 920px) {
  .mode-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .pagination-row {
    justify-content: flex-start;
  }
}
</style>
