<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { AUTH_CHANGED_EVENT, api, getSavedUser } from '../../api/client'
import { hasAnyPermission } from '../../composables/accessControl'
import { useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

const questionTypeOptions = [
  { value: '', label: '全部题型' },
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTI', label: '多选题' },
  { value: 'JUDGE', label: '判断题' },
  { value: 'FILL', label: '填空题' },
  { value: 'SHORT', label: '简答题' },
]

const questionTypeLabelMap = Object.fromEntries(
  questionTypeOptions.filter((item) => item.value).map((item) => [item.value, item.label])
)

const questionTypeRankMap = {
  SINGLE: 1,
  MULTI: 1,
  JUDGE: 2,
  FILL: 3,
  SHORT: 4,
}

const defaultScoreByType = {
  SINGLE: 5,
  MULTI: 5,
  JUDGE: 2,
  FILL: 5,
  SHORT: 10,
}

const activeMode = ref('')
const composeStep = ref(0)
const showBankDialog = ref(false)
const collapseActiveNames = ref([])
const bankTableRef = ref(null)
const authVersion = ref(0)

const form = reactive({
  name: '',
  timeLimitMinutes: 90,
  questions: [],
})

const bankFilters = reactive({
  keyword: '',
  type: '',
  knowledgePoint: '',
})

const bankPagination = reactive({
  pageNo: 1,
  pageSize: 8,
})

const questionBank = ref([])
const selectedBankRows = ref([])
const pendingSelectionMap = ref(new Map())
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
const modeOptions = computed(() => availableModes.value.map((item) => ({
  value: item,
  label: item === 'compose' ? '组卷模式' : '查卷模式',
})))

const questionBankMap = computed(() => {
  const map = new Map()
  questionBank.value.forEach((item) => {
    map.set(String(item.id), item)
  })
  return map
})

const estimatedScore = computed(() =>
  form.questions.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
)

const normalizedKeyword = computed(() => bankFilters.keyword.trim().toLowerCase())
const knowledgePointOptions = computed(() => {
  const set = new Set()
  questionBank.value.forEach((item) => {
    const value = String(item?.knowledgePoint || '').trim()
    if (value) {
      set.add(value)
    }
  })
  return [...set].sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
})
const filteredQuestionBank = computed(() =>
  questionBank.value.filter((item) => {
    const typeMatched = !bankFilters.type || item.type === bankFilters.type
    if (!typeMatched) return false
    const knowledgePointMatched =
      !bankFilters.knowledgePoint || String(item.knowledgePoint || '').trim() === bankFilters.knowledgePoint
    if (!knowledgePointMatched) return false
    if (!normalizedKeyword.value) return true
    const fullText = `${item.id || ''} ${item.stem || ''} ${item.knowledgePoint || ''}`.toLowerCase()
    return fullText.includes(normalizedKeyword.value)
  })
)

const pagedQuestionBank = computed(() => {
  const start = (bankPagination.pageNo - 1) * bankPagination.pageSize
  return filteredQuestionBank.value.slice(start, start + bankPagination.pageSize)
})

const groupedSelectedQuestions = computed(() => {
  const groups = new Map()

  form.questions.forEach((item) => {
    const type = getQuestionType(item.questionId, item.type)
    const key = type || 'UNKNOWN'
    if (!groups.has(key)) {
      groups.set(key, {
        type: key,
        label: renderTypeLabel(key),
        items: [],
      })
    }
    groups.get(key).items.push(item)
  })

  return [...groups.values()]
    .map((group) => ({
      ...group,
      totalScore: group.items.reduce((sum, item) => sum + (Number(item.score) || 0), 0),
      count: group.items.length,
    }))
    .sort((a, b) => {
      const rankA = questionTypeRankMap[a.type] || 99
      const rankB = questionTypeRankMap[b.type] || 99
      return rankA - rankB
    })
})

const paperDetailQuestions = computed(() => {
  if (!paperDetail.value?.questions) return []
  return [...paperDetail.value.questions].sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0))
})
const pendingSelectionList = computed(() => [...pendingSelectionMap.value.values()])
const pendingSelectionCount = computed(() => pendingSelectionMap.value.size)
const paperRecords = computed(() => (Array.isArray(paperPage.value.records) ? paperPage.value.records : []))

watch(
  () => [bankFilters.keyword, bankFilters.type, bankFilters.knowledgePoint],
  () => {
    bankPagination.pageNo = 1
  }
)

watch(
  groupedSelectedQuestions,
  (groups) => {
    collapseActiveNames.value = groups.map((item) => item.type)
  },
  { immediate: true }
)

watch(pagedQuestionBank, async () => {
  await syncTableSelection()
})

watch(showBankDialog, async (open) => {
  if (open) {
    await syncTableSelection()
  }
})

const syncModeByPermission = () => {
  if (!availableModes.value.length) {
    activeMode.value = ''
    showBankDialog.value = false
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

watch(canBrowseQuestionBank, async (enabled) => {
  if (!enabled) {
    questionBank.value = []
    showBankDialog.value = false
    pendingSelectionMap.value = new Map()
    selectedBankRows.value = []
    return
  }
  if (canComposePaper.value && activeMode.value === 'compose' && !questionBank.value.length) {
    await loadQuestionBank()
  }
})

const onAuthChanged = () => {
  authVersion.value += 1
}

const renderTypeLabel = (type) => questionTypeLabelMap[type] || type || '-'
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

const getQuestionType = (questionId, fallbackType) => {
  if (fallbackType) return fallbackType
  return questionBankMap.value.get(String(questionId))?.type || ''
}

const getQuestionStem = (questionId, fallbackStem) => {
  if (fallbackStem) return fallbackStem
  return questionBankMap.value.get(String(questionId))?.stem || '-'
}

const resetQuestionOrder = () => {
  form.questions.forEach((item, index) => {
    item.orderNo = index + 1
  })
}

const addQuestionFromBank = (question, silentDuplicate = false) => {
  const questionId = String(question?.id || '').trim()
  if (!questionId) return false
  if (form.questions.some((item) => item.questionId === questionId)) {
    if (!silentDuplicate) {
      ElMessage.warning('该题目已在试卷中')
    }
    return false
  }
  form.questions.push({
    questionId,
    score: defaultScoreByType[question.type] || 5,
    orderNo: form.questions.length + 1,
    type: question.type,
    stem: question.stem,
  })
  return true
}

const addSelectedQuestions = (closeAfterAdd = false) => {
  if (!pendingSelectionCount.value) {
    ElMessage.warning('请先在题库列表勾选题目')
    return
  }

  let added = 0
  pendingSelectionList.value.forEach((row) => {
    if (addQuestionFromBank(row, true)) {
      added += 1
    }
  })
  resetQuestionOrder()

  if (added === 0) {
    ElMessage.warning('勾选题目已全部存在，无新增')
    return
  }

  ElMessage.success(`已加入 ${added} 道题目`)
  pendingSelectionMap.value = new Map()
  selectedBankRows.value = []
  if (closeAfterAdd) {
    showBankDialog.value = false
  }
}

const onBankSelectionChange = (rows) => {
  selectedBankRows.value = Array.isArray(rows) ? rows : []
  const currentPageIds = new Set(pagedQuestionBank.value.map((item) => String(item.id)))
  const nextMap = new Map(pendingSelectionMap.value)
  currentPageIds.forEach((id) => {
    nextMap.delete(id)
  })
  selectedBankRows.value.forEach((row) => {
    nextMap.set(String(row.id), row)
  })
  pendingSelectionMap.value = nextMap
}

const addToPendingFromRow = async (row) => {
  const id = String(row?.id || '').trim()
  if (!id) return
  const nextMap = new Map(pendingSelectionMap.value)
  nextMap.set(id, row)
  pendingSelectionMap.value = nextMap
  await syncTableSelection()
}

const removePendingQuestion = async (questionId) => {
  const id = String(questionId || '').trim()
  if (!id) return
  const nextMap = new Map(pendingSelectionMap.value)
  nextMap.delete(id)
  pendingSelectionMap.value = nextMap
  await syncTableSelection()
}

const clearPendingSelection = async () => {
  pendingSelectionMap.value = new Map()
  selectedBankRows.value = []
  await syncTableSelection()
}

const removeQuestion = (index) => {
  form.questions.splice(index, 1)
  resetQuestionOrder()
}

const moveQuestion = (index, offset) => {
  const target = index + offset
  if (target < 0 || target >= form.questions.length) return
  const current = form.questions[index]
  form.questions.splice(index, 1)
  form.questions.splice(target, 0, current)
  resetQuestionOrder()
}

const sortByBusinessTypeOrder = () => {
  if (!form.questions.length) return
  form.questions.sort((a, b) => {
    const rankA = questionTypeRankMap[getQuestionType(a.questionId, a.type)] || 99
    const rankB = questionTypeRankMap[getQuestionType(b.questionId, b.type)] || 99
    if (rankA !== rankB) return rankA - rankB
    return (a.orderNo || 0) - (b.orderNo || 0)
  })
  resetQuestionOrder()
}

const clearAllQuestions = () => {
  form.questions.splice(0, form.questions.length)
}

const openBankSelector = () => {
  if (!canComposePaper.value) {
    ElMessage.warning('当前账号没有创建试卷权限')
    return
  }
  if (!canBrowseQuestionBank.value) {
    ElMessage.warning('当前账号没有题库读取权限，无法从题库选题')
    return
  }
  showBankDialog.value = true
}

const loadQuestionBank = async () => {
  if (!canBrowseQuestionBank.value) {
    questionBank.value = []
    return
  }
  const data = await run('listQuestions', () => api.listQuestions())
  if (data) {
    questionBank.value = Array.isArray(data) ? data : []
    if (pendingSelectionMap.value.size > 0) {
      const latestMap = new Map(questionBank.value.map((item) => [String(item.id), item]))
      const mergedMap = new Map()
      pendingSelectionMap.value.forEach((oldValue, id) => {
        mergedMap.set(id, latestMap.get(id) || oldValue)
      })
      pendingSelectionMap.value = mergedMap
    }
    await syncTableSelection()
  }
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

const syncTableSelection = async () => {
  await nextTick()
  const table = bankTableRef.value
  if (!table) return
  table.clearSelection()
  pagedQuestionBank.value.forEach((row) => {
    if (pendingSelectionMap.value.has(String(row.id))) {
      table.toggleRowSelection(row, true)
    }
  })
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

  if (composeStep.value === 1) {
    if (!form.questions.length) {
      ElMessage.warning('请先加入至少一道题目')
      return
    }
  }

  composeStep.value = Math.min(2, composeStep.value + 1)
}

const goPrevStep = () => {
  composeStep.value = Math.max(0, composeStep.value - 1)
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

  const unknownQuestion = form.questions.find((item) => !questionBankMap.value.has(String(item.questionId)))
  if (unknownQuestion) {
    ElMessage.warning(`题目 ${unknownQuestion.questionId} 不在当前老师题库中，请重新选择`)
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
    ElMessage.warning('请先从列表选择试卷')
    return
  }
  const data = await run('getPaper', () => api.getPaper(targetPaperId))
  if (data) {
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
  if (canComposePaper.value && canBrowseQuestionBank.value) {
    await loadQuestionBank()
  }
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
          <p class="block-sub">采用三步向导拆分流程，降低单屏复杂度并提升可读性。</p>
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
            <p class="block-sub">题库选择改为弹窗，不再挤压主画布。</p>
          </div>
          <div class="action-row">
            <el-button :disabled="!canBrowseQuestionBank" @click="openBankSelector">从题库选择题目</el-button>
            <el-button @click="sortByBusinessTypeOrder">按题型规则自动排序</el-button>
            <el-button type="danger" plain @click="clearAllQuestions">清空题目</el-button>
          </div>
        </div>

        <el-empty v-if="!form.questions.length" description="尚未选题，请点击“从题库选择题目”" />

        <el-collapse v-else v-model="collapseActiveNames">
          <el-collapse-item
            v-for="group in groupedSelectedQuestions"
            :key="group.type"
            :name="group.type"
          >
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

    <el-dialog
      v-model="showBankDialog"
      title="从题库选择题目"
      class="paper-bank-dialog"
      width="92vw"
      top="3vh"
      destroy-on-close
    >
      <div class="bank-dialog-layout">
        <section class="bank-main stack-gap">
          <div class="bank-filter-grid">
            <el-input v-model="bankFilters.keyword" clearable placeholder="关键词（ID/题干/知识点）" />
            <el-select v-model="bankFilters.type" clearable placeholder="题型筛选">
              <el-option
                v-for="item in questionTypeOptions"
                :key="item.value || 'ALL'"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select v-model="bankFilters.knowledgePoint" clearable filterable placeholder="知识点筛选">
              <el-option
                v-for="item in knowledgePointOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
            <el-button :loading="loading.listQuestions" @click="loadQuestionBank">刷新题库</el-button>
          </div>

          <div class="action-row bank-toolbar">
            <el-button type="primary" plain @click="addSelectedQuestions(false)">加入勾选题目</el-button>
            <span class="hint-text">当前可选 {{ filteredQuestionBank.length }} 题，已暂存 {{ pendingSelectionCount }} 题</span>
          </div>

          <el-table
            ref="bankTableRef"
            :data="pagedQuestionBank"
            size="small"
            max-height="460"
            @selection-change="onBankSelectionChange"
          >
            <el-table-column type="selection" width="50" />
            <el-table-column prop="id" label="ID" min-width="150" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ renderTypeLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column prop="difficulty" label="难度" width="80" />
            <el-table-column prop="knowledgePoint" label="知识点" min-width="120" show-overflow-tooltip />
            <el-table-column prop="stem" label="题干" min-width="280" show-overflow-tooltip />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link @click="addToPendingFromRow(row)">暂存</el-button>
                  <el-button link type="primary" @click="addQuestionFromBank(row)">加入</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="pagination-row"
            background
            layout="prev, pager, next, sizes, total"
            :current-page="bankPagination.pageNo"
            :page-size="bankPagination.pageSize"
            :page-sizes="[8, 16, 24, 32]"
            :total="filteredQuestionBank.length"
            @update:current-page="(val) => (bankPagination.pageNo = val)"
            @update:page-size="(val) => ((bankPagination.pageSize = val), (bankPagination.pageNo = 1))"
          />
        </section>

        <aside class="bank-pending">
          <div class="pending-head">
            <h4>待加入列表</h4>
            <span>{{ pendingSelectionCount }} 题</span>
          </div>

          <div class="action-row pending-actions">
            <el-button type="primary" :disabled="!pendingSelectionCount" @click="addSelectedQuestions(false)">
              批量加入试卷
            </el-button>
            <el-button :disabled="!pendingSelectionCount" @click="clearPendingSelection">清空暂存</el-button>
          </div>

          <el-empty v-if="!pendingSelectionCount" :image-size="70" description="暂存区为空，先在左侧勾选或暂存题目" />

          <div v-else class="pending-list">
            <article v-for="item in pendingSelectionList" :key="item.id" class="pending-item">
              <div class="pending-item-head">
                <strong>{{ renderTypeLabel(item.type) }}</strong>
                <el-button text type="danger" @click="removePendingQuestion(item.id)">移除</el-button>
              </div>
              <p class="pending-meta">ID: {{ item.id }} · 难度: {{ item.difficulty || '-' }}</p>
              <p class="pending-stem">{{ item.stem || '-' }}</p>
            </article>
          </div>
        </aside>
      </div>

      <template #footer>
        <div class="action-row">
          <el-button @click="showBankDialog = false">关闭</el-button>
          <el-button type="primary" :disabled="!pendingSelectionCount" @click="addSelectedQuestions(true)">
            加入并关闭（{{ pendingSelectionCount }}）
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mode-head {
  align-items: center;
}

.table-actions {
  margin: 8px 0;
  align-items: center;
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

.paper-bank-dialog :deep(.el-dialog) {
  max-width: 1680px;
}

.paper-bank-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
}

.bank-dialog-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: start;
  min-height: calc(92vh - 220px);
}

.bank-main,
.bank-pending {
  min-width: 0;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.bank-main {
  overflow: hidden;
}

.bank-filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.bank-toolbar {
  margin-top: 4px;
}

.pending-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.pending-head h4 {
  margin: 0;
  font-size: 16px;
  color: var(--ink-main);
}

.pending-head span {
  font-size: 12px;
  color: var(--ink-soft);
}

.pending-actions {
  margin-bottom: 12px;
}

.pending-list {
  max-height: calc(92vh - 360px);
  min-height: 220px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}

.pending-item {
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.88);
}

.pending-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pending-meta {
  margin: 4px 0;
  font-size: 12px;
  color: var(--ink-soft);
}

.pending-stem {
  margin: 0;
  font-size: 13px;
  color: var(--ink-main);
  line-height: 1.45;
  word-break: break-word;
}

@media (max-width: 920px) {
  .mode-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .bank-dialog-layout {
    grid-template-columns: 1fr;
  }

  .bank-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pagination-row {
    justify-content: flex-start;
  }
}

@media (max-width: 680px) {
  .bank-filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>


