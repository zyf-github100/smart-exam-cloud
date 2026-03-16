import { computed, reactive, ref } from 'vue'
import { api } from '../api/client'

const questionTypeOptions = [
  { value: '', label: '\u5168\u90e8\u9898\u578b' },
  { value: 'SINGLE', label: '\u5355\u9009\u9898' },
  { value: 'MULTI', label: '\u591a\u9009\u9898' },
  { value: 'JUDGE', label: '\u5224\u65ad\u9898' },
  { value: 'FILL', label: '\u586b\u7a7a\u9898' },
  { value: 'SHORT', label: '\u7b80\u7b54\u9898' },
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

const composeStep = ref(0)
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
  pageSize: 10,
})

const questionBank = ref([])
const questionBankTotal = ref(0)
const questionBankBaseTotal = ref(0)
const questionLookup = ref(new Map())
const pendingSelectionMap = ref(new Map())
const questionBankLoading = ref(false)
const knowledgePointOptions = ref([])
const knowledgePointLoading = ref(false)
const knowledgePointLoadedType = ref('')

const questionBankMap = computed(() => new Map(questionLookup.value))

const estimatedScore = computed(() =>
  form.questions.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
)

const filteredQuestionBank = computed(() => questionBank.value)
const pagedQuestionBank = computed(() => questionBank.value)

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

const pendingSelectionList = computed(() => [...pendingSelectionMap.value.values()])
const pendingSelectionCount = computed(() => pendingSelectionMap.value.size)

function normalizeQuestionRecord(question) {
  if (!question?.id) {
    return null
  }
  return {
    ...question,
    id: String(question.id),
  }
}

function upsertQuestionLookup(records) {
  if (!Array.isArray(records) || !records.length) {
    return
  }
  const nextMap = new Map(questionLookup.value)
  records.forEach((record) => {
    const normalized = normalizeQuestionRecord(record)
    if (normalized) {
      nextMap.set(String(normalized.id), normalized)
    }
  })
  questionLookup.value = nextMap
}

function syncPendingSelectionWithLookup() {
  if (!pendingSelectionMap.value.size) {
    return
  }
  const nextMap = new Map()
  pendingSelectionMap.value.forEach((record, id) => {
    nextMap.set(id, questionLookup.value.get(id) || record)
  })
  pendingSelectionMap.value = nextMap
}

function hasActiveBankFilters() {
  return Boolean(bankFilters.keyword.trim() || bankFilters.type || bankFilters.knowledgePoint)
}

function renderTypeLabel(type) {
  return questionTypeLabelMap[type] || type || '-'
}

function getQuestionType(questionId, fallbackType) {
  if (fallbackType) return fallbackType
  return questionBankMap.value.get(String(questionId))?.type || ''
}

function getQuestionStem(questionId, fallbackStem) {
  if (fallbackStem) return fallbackStem
  return questionBankMap.value.get(String(questionId))?.stem || '-'
}

function resetQuestionOrder() {
  form.questions.forEach((item, index) => {
    item.orderNo = index + 1
  })
}

function addQuestionFromBank(question, silentDuplicate = false) {
  const normalized = normalizeQuestionRecord(question)
  const questionId = String(normalized?.id || '').trim()
  if (!questionId) return false
  if (form.questions.some((item) => item.questionId === questionId)) {
    if (!silentDuplicate) {
      ElMessage.warning('\u8be5\u9898\u76ee\u5df2\u5728\u8bd5\u5377\u4e2d')
    }
    return false
  }

  upsertQuestionLookup([normalized])
  form.questions.push({
    questionId,
    score: defaultScoreByType[normalized.type] || 5,
    orderNo: form.questions.length + 1,
    type: normalized.type,
    stem: normalized.stem,
  })
  return true
}

function addPendingQuestionsToPaper() {
  if (!pendingSelectionCount.value) {
    ElMessage.warning('\u8bf7\u5148\u5728\u9898\u5e93\u5217\u8868\u52fe\u9009\u9898\u76ee')
    return 0
  }

  let added = 0
  pendingSelectionList.value.forEach((row) => {
    if (addQuestionFromBank(row, true)) {
      added += 1
    }
  })
  resetQuestionOrder()

  if (added === 0) {
    ElMessage.warning('\u52fe\u9009\u9898\u76ee\u5df2\u5168\u90e8\u5b58\u5728\uff0c\u65e0\u65b0\u589e')
    return 0
  }

  pendingSelectionMap.value = new Map()
  ElMessage.success(`\u5df2\u52a0\u5165 ${added} \u9053\u9898\u76ee`)
  return added
}

function replacePendingSelectionByPage(pageRows, rows) {
  const currentPageIds = new Set((Array.isArray(pageRows) ? pageRows : []).map((item) => String(item.id)))
  const nextMap = new Map(pendingSelectionMap.value)
  currentPageIds.forEach((id) => {
    nextMap.delete(id)
  })
  ;(Array.isArray(rows) ? rows : []).forEach((row) => {
    const normalized = normalizeQuestionRecord(row)
    if (normalized) {
      nextMap.set(String(normalized.id), normalized)
    }
  })
  pendingSelectionMap.value = nextMap
}

function addToPendingFromRow(row) {
  const normalized = normalizeQuestionRecord(row)
  const id = String(normalized?.id || '').trim()
  if (!id) return
  const nextMap = new Map(pendingSelectionMap.value)
  nextMap.set(id, normalized)
  pendingSelectionMap.value = nextMap
}

function removePendingQuestion(questionId) {
  const id = String(questionId || '').trim()
  if (!id) return
  const nextMap = new Map(pendingSelectionMap.value)
  nextMap.delete(id)
  pendingSelectionMap.value = nextMap
}

function clearPendingSelection() {
  pendingSelectionMap.value = new Map()
}

function removeQuestion(index) {
  form.questions.splice(index, 1)
  resetQuestionOrder()
}

function moveQuestion(index, offset) {
  const target = index + offset
  if (target < 0 || target >= form.questions.length) return
  const current = form.questions[index]
  form.questions.splice(index, 1)
  form.questions.splice(target, 0, current)
  resetQuestionOrder()
}

function sortByBusinessTypeOrder() {
  if (!form.questions.length) return

  form.questions.sort((a, b) => {
    const rankA = questionTypeRankMap[getQuestionType(a.questionId, a.type)] || 99
    const rankB = questionTypeRankMap[getQuestionType(b.questionId, b.type)] || 99
    if (rankA !== rankB) return rankA - rankB
    return (a.orderNo || 0) - (b.orderNo || 0)
  })
  resetQuestionOrder()
}

function clearAllQuestions() {
  form.questions.splice(0, form.questions.length)
  resetQuestionOrder()
}

function setComposeStep(step) {
  composeStep.value = Math.min(2, Math.max(0, Number(step) || 0))
}

function clearQuestionBankState() {
  questionBank.value = []
  questionBankTotal.value = 0
  questionBankBaseTotal.value = 0
  knowledgePointOptions.value = []
  knowledgePointLoadedType.value = ''
  questionLookup.value = new Map()
  pendingSelectionMap.value = new Map()
}

function resetWorkspace() {
  form.name = ''
  form.timeLimitMinutes = 90
  form.questions.splice(0, form.questions.length)
  bankFilters.keyword = ''
  bankFilters.type = ''
  bankFilters.knowledgePoint = ''
  bankPagination.pageNo = 1
  bankPagination.pageSize = 10
  composeStep.value = 0
  clearQuestionBankState()
}

async function loadQuestionBank(options = {}) {
  const { force = false } = options
  if (questionBankLoading.value && !force) {
    return questionBank.value
  }

  questionBankLoading.value = true
  try {
    const payload = await api.listQuestions({
      keyword: bankFilters.keyword.trim() || undefined,
      type: bankFilters.type || undefined,
      knowledgePoint: bankFilters.knowledgePoint || undefined,
      page: bankPagination.pageNo,
      size: bankPagination.pageSize,
    })
    const records = Array.isArray(payload?.records)
      ? payload.records.map((item) => normalizeQuestionRecord(item)).filter(Boolean)
      : []

    questionBank.value = records
    questionBankTotal.value = Number(payload?.total || 0)
    bankPagination.pageNo = Number(payload?.page || bankPagination.pageNo)
    bankPagination.pageSize = Number(payload?.size || bankPagination.pageSize)

    if (!hasActiveBankFilters()) {
      questionBankBaseTotal.value = questionBankTotal.value
    }

    upsertQuestionLookup(records)
    syncPendingSelectionWithLookup()
    return questionBank.value
  } catch (error) {
    ElMessage.error(error?.message || '\u52a0\u8f7d\u9898\u5e93\u5931\u8d25')
    return null
  } finally {
    questionBankLoading.value = false
  }
}

async function loadKnowledgePointOptions(options = {}) {
  const { force = false } = options
  const currentType = String(bankFilters.type || '')
  if (knowledgePointLoading.value && !force) {
    return knowledgePointOptions.value
  }
  if (!force && knowledgePointLoadedType.value === currentType && knowledgePointOptions.value.length) {
    return knowledgePointOptions.value
  }

  knowledgePointLoading.value = true
  try {
    const payload = await api.listQuestionKnowledgePoints({
      type: currentType || undefined,
    })
    const records = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.records)
        ? payload.records
        : []
    const normalized = records
      .map((item) => String(item || '').trim())
      .filter(Boolean)

    if (bankFilters.knowledgePoint && !normalized.includes(bankFilters.knowledgePoint)) {
      normalized.unshift(bankFilters.knowledgePoint)
    }

    knowledgePointOptions.value = [...new Set(normalized)].sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
    knowledgePointLoadedType.value = currentType
    return knowledgePointOptions.value
  } catch (error) {
    ElMessage.error(error?.message || '\u52a0\u8f7d\u77e5\u8bc6\u70b9\u5931\u8d25')
    return []
  } finally {
    knowledgePointLoading.value = false
  }
}

async function ensureQuestionsCached(questionIds) {
  const missingIds = [...new Set((Array.isArray(questionIds) ? questionIds : []).map((item) => String(item || '').trim()))]
    .filter(Boolean)
    .filter((item) => !questionLookup.value.has(item))

  if (!missingIds.length) {
    return questionBankMap.value
  }

  const results = await Promise.all(
    missingIds.map(async (questionId) => {
      try {
        const payload = await api.getQuestion(questionId)
        return normalizeQuestionRecord(payload)
      } catch {
        return null
      }
    })
  )

  upsertQuestionLookup(results.filter(Boolean))
  syncPendingSelectionWithLookup()
  return questionBankMap.value
}

export const usePaperComposerWorkspace = () => ({
  questionTypeOptions,
  composeStep,
  form,
  bankFilters,
  bankPagination,
  questionBank,
  questionBankTotal,
  questionBankBaseTotal,
  questionBankMap,
  pendingSelectionMap,
  questionBankLoading,
  knowledgePointOptions,
  knowledgePointLoading,
  estimatedScore,
  filteredQuestionBank,
  pagedQuestionBank,
  groupedSelectedQuestions,
  pendingSelectionList,
  pendingSelectionCount,
  renderTypeLabel,
  getQuestionType,
  getQuestionStem,
  resetQuestionOrder,
  addQuestionFromBank,
  addPendingQuestionsToPaper,
  replacePendingSelectionByPage,
  addToPendingFromRow,
  removePendingQuestion,
  clearPendingSelection,
  removeQuestion,
  moveQuestion,
  sortByBusinessTypeOrder,
  clearAllQuestions,
  setComposeStep,
  clearQuestionBankState,
  resetWorkspace,
  loadQuestionBank,
  loadKnowledgePointOptions,
  ensureQuestionsCached,
})
