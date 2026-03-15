<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { AUTH_CHANGED_EVENT, api, getSavedUser } from '../../api/client'
import { hasAnyPermission } from '../../composables/accessControl'
import { getPaperDirectory, getPublishedExamDirectory, getUserDirectory } from '../../composables/useReferenceData'
import { useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()
const AUTO_REFRESH_INTERVAL_MS = 8000

const gradingStatus = ref('')
const gradingPaperId = ref('')
const gradingTasks = ref([])
const selectedTask = ref(null)
const studentNameById = ref({})
const examPaperByExamId = ref({})
const paperNameById = ref({})
const manualScoreForm = reactive({
  scores: [],
})
const manualScoreResult = ref(null)
const authVersion = ref(0)
let refreshTimer = null

const currentUser = computed(() => {
  authVersion.value
  return getSavedUser()
})
const canViewTasks = computed(() => hasAnyPermission(currentUser.value, ['GRADING_TASK_VIEW']))
const canManualScore = computed(() => hasAnyPermission(currentUser.value, ['GRADING_MANUAL_SCORE']))
const canLoadUserDirectory = computed(() => hasAnyPermission(currentUser.value, ['USER_LIST_VIEW']))
const canLoadExamDirectory = computed(() => hasAnyPermission(currentUser.value, ['EXAM_CREATE']))
const canLoadPaperDirectory = computed(() => hasAnyPermission(currentUser.value, ['PAPER_DETAIL']))
const subjectiveQuestionCount = computed(() => manualScoreForm.scores.length)

const toStatusText = (status) => {
  const value = String(status || '').toUpperCase()
  if (value === 'MANUAL_REQUIRED') return '待人工评分'
  if (value === 'AUTO_DONE') return '自动批改完成'
  if (value === 'DONE') return '已完成'
  return status || '-'
}

const isTaskManualRequired = (task) => String(task?.status || '').toUpperCase() === 'MANUAL_REQUIRED'
const isSelectedTask = (task) => String(task?.id || '') === String(selectedTask.value?.id || '')

const clearManualScoreSelection = () => {
  selectedTask.value = null
  manualScoreForm.scores = []
}

const buildQuestionLabel = (item, index) => {
  const orderNo = String(item?.orderNo ?? '').trim()
  if (orderNo) return `第 ${orderNo} 题`
  return `主观题 ${index + 1}`
}

const buildManualScoreRows = (task) => {
  const questionScores = Array.isArray(task?.questionScores) ? task.questionScores : []
  return questionScores
    .filter((item) => !item?.objective)
    .map((item, index) => {
      const maxScore = Number(item?.maxScore)
      const hasMaxScore = Number.isFinite(maxScore)
      return {
        questionId: String(item?.questionId || '').trim(),
        displayLabel: buildQuestionLabel(item, index),
        gotScore: Number(item?.gotScore ?? 0),
        comment: String(item?.comment || ''),
        maxScore: hasMaxScore ? maxScore : null,
      }
    })
    .filter((item) => item.questionId)
}

const loadStudentDirectory = async (options = {}) => {
  if (!canLoadUserDirectory.value) {
    studentNameById.value = {}
    return
  }

  const data = await run('users', () => getUserDirectory(options), {
    errorMessage: '加载学生姓名失败',
  })
  if (!data) return

  const users = Array.isArray(data) ? data : []
  const nameMap = {}
  users.forEach((item) => {
    const id = String(item?.id || '').trim()
    if (!id) return

    const role = String(item?.role || '').toUpperCase()
    if (role && role !== 'STUDENT') return

    const realName = String(item?.realName || '').trim()
    const username = String(item?.username || '').trim()
    nameMap[id] = realName || username || `学生${id}`
  })

  studentNameById.value = nameMap
}

const loadExamDirectory = async (options = {}) => {
  if (!canLoadExamDirectory.value) {
    examPaperByExamId.value = {}
    return
  }

  const data = await run('publishedExams', () => getPublishedExamDirectory(options), {
    errorMessage: '加载试卷筛选失败',
  })
  if (!data) return

  const exams = Array.isArray(data) ? data : []
  const nextExamPaperMap = {}
  exams.forEach((item) => {
    const examId = String(item?.id || item?.examId || '').trim()
    const paperId = String(item?.paperId || '').trim()
    if (!examId || !paperId) return
    nextExamPaperMap[examId] = paperId
  })

  examPaperByExamId.value = nextExamPaperMap
  if (gradingPaperId.value && !paperFilterOptions.value.some((item) => item.paperId === gradingPaperId.value)) {
    gradingPaperId.value = ''
  }
  ensureSelectedTaskVisible()
}

const loadPaperDirectory = async (options = {}) => {
  if (!canLoadPaperDirectory.value) {
    paperNameById.value = {}
    return
  }

  const data = await run('papers', () =>
    getPaperDirectory({
      page: 1,
      size: 200,
      force: options.force,
    }),
  {
    errorMessage: '加载试卷名称失败',
  })
  if (!data) return

  const records = Array.isArray(data?.records) ? data.records : []
  const nextPaperNameMap = {}
  records.forEach((item) => {
    const paperId = String(item?.id || '').trim()
    if (!paperId) return
    const paperName = String(item?.name || '').trim()
    if (!paperName) return
    nextPaperNameMap[paperId] = paperName
  })
  paperNameById.value = nextPaperNameMap
}

const refreshPaperFilters = async () => {
  await loadExamDirectory({ force: true })
  await loadPaperDirectory({ force: true })
}

const resolveStudentName = (task) => {
  const userId = String(task?.userId || '').trim()
  if (!userId) return '-'

  const name = studentNameById.value[userId]
  if (name) return name

  return `学号 ${userId}`
}

const resolveTaskPaperId = (task) => {
  const examId = String(task?.examId || '').trim()
  if (!examId) return ''
  return String(examPaperByExamId.value[examId] || '').trim()
}

const resolvePaperDisplayName = (paperId) => {
  const normalizedPaperId = String(paperId || '').trim()
  if (!normalizedPaperId) return '-'
  const paperName = String(paperNameById.value[normalizedPaperId] || '').trim()
  return paperName || `试卷 ${normalizedPaperId}`
}

const paperFilterOptions = computed(() => {
  const countByPaper = new Map()
  gradingTasks.value.forEach((task) => {
    const paperId = resolveTaskPaperId(task)
    if (!paperId) return
    countByPaper.set(paperId, (countByPaper.get(paperId) || 0) + 1)
  })
  return Array.from(countByPaper.entries())
    .map(([paperId, count]) => ({
      paperId,
      label: `${resolvePaperDisplayName(paperId)}（${count}）`,
    }))
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-Hans-CN'))
})

const displayedGradingTasks = computed(() => {
  const paperId = String(gradingPaperId.value || '').trim()
  if (!paperId) return gradingTasks.value
  return gradingTasks.value.filter((task) => resolveTaskPaperId(task) === paperId)
})

const ensureSelectedTaskVisible = () => {
  const taskId = String(selectedTask.value?.id || '').trim()
  if (!taskId) return
  const exists = displayedGradingTasks.value.some((item) => String(item?.id || '') === taskId)
  if (!exists) {
    clearManualScoreSelection()
    ElMessage.info('当前筛选条件下已找不到已载入任务，请重新选择')
  }
}

const loadGradingTasks = async () => {
  if (!canViewTasks.value) {
    gradingTasks.value = []
    clearManualScoreSelection()
    return
  }
  const data = await run('list', () => api.listGradingTasks(gradingStatus.value))
  if (data) {
    gradingTasks.value = Array.isArray(data) ? data : []
    ensureSelectedTaskVisible()
  }
}

const clearRefreshTimer = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const setupAutoRefresh = () => {
  clearRefreshTimer()
  if (!canViewTasks.value) return
  refreshTimer = window.setInterval(() => {
    if (!loading.list) {
      loadGradingTasks()
    }
  }, AUTO_REFRESH_INTERVAL_MS)
}

const fillManualScoreTask = (task) => {
  if (!canManualScore.value) return
  if (!isTaskManualRequired(task)) {
    ElMessage.warning('该任务当前不是待人工评分状态')
    return
  }

  const scoreRows = buildManualScoreRows(task)
  if (!scoreRows.length) {
    ElMessage.warning('该任务没有需要人工评分的主观题')
    return
  }

  selectedTask.value = task
  manualScoreForm.scores = scoreRows
}

const validateManualScores = () => {
  if (!manualScoreForm.scores.length) {
    return '当前任务没有可提交的主观题评分'
  }

  for (let i = 0; i < manualScoreForm.scores.length; i += 1) {
    const item = manualScoreForm.scores[i]
    const score = Number(item.gotScore)
    if (!Number.isFinite(score) || score < 0) {
      return `${item.displayLabel} 分数不合法`
    }
    if (item.maxScore !== null && score > item.maxScore) {
      return `${item.displayLabel} 不能超过满分 ${item.maxScore}`
    }
  }

  return ''
}

const submitManualScore = async () => {
  if (!canManualScore.value) {
    ElMessage.warning('当前账号没有人工评分权限')
    return
  }

  const taskId = String(selectedTask.value?.id || '').trim()
  if (!taskId) {
    ElMessage.warning('请先在任务列表点击“开始评分”')
    return
  }

  const validationError = validateManualScores()
  if (validationError) {
    ElMessage.warning(validationError)
    return
  }

  const scores = manualScoreForm.scores.map((item) => ({
    questionId: item.questionId,
    gotScore: Number(item.gotScore),
    comment: item.comment?.trim() || '',
  }))

  const data = await run('manual', () => api.manualScore(taskId, { scores }), {
    successMessage: '人工评分提交成功，正在推送最终成绩到报表',
  })

  if (data) {
    manualScoreResult.value = data
    await loadGradingTasks()

    const latestTask = gradingTasks.value.find((item) => String(item?.id || '') === taskId)
    if (!latestTask || !isTaskManualRequired(latestTask)) {
      clearManualScoreSelection()
    }
  }
}

const onAuthChanged = () => {
  authVersion.value += 1
}

watch(
  () => canViewTasks.value,
  async (enabled) => {
    if (!enabled) {
      gradingPaperId.value = ''
      gradingTasks.value = []
      examPaperByExamId.value = {}
      paperNameById.value = {}
      clearManualScoreSelection()
      clearRefreshTimer()
      return
    }
    await loadGradingTasks()
    setupAutoRefresh()
    await loadStudentDirectory()
    await loadExamDirectory()
    await loadPaperDirectory()
  },
  { immediate: true }
)

watch(
  () => canLoadUserDirectory.value,
  async (enabled) => {
    if (!enabled) {
      studentNameById.value = {}
      return
    }
    await loadStudentDirectory()
  }
)

watch(
  () => canLoadExamDirectory.value,
  async (enabled) => {
    if (!enabled) {
      gradingPaperId.value = ''
      examPaperByExamId.value = {}
      ensureSelectedTaskVisible()
      return
    }
    await loadExamDirectory()
  }
)

watch(
  () => canLoadPaperDirectory.value,
  async (enabled) => {
    if (!enabled) {
      paperNameById.value = {}
      return
    }
    await loadPaperDirectory()
  }
)

watch(gradingStatus, async () => {
  if (!canViewTasks.value) return
  await loadGradingTasks()
})

watch(paperFilterOptions, (options) => {
  if (gradingPaperId.value && !options.some((item) => item.paperId === gradingPaperId.value)) {
    gradingPaperId.value = ''
  }
})

watch(gradingPaperId, () => {
  ensureSelectedTaskVisible()
})

onMounted(() => {
  window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
  clearRefreshTimer()
})
</script>

<template>
  <div class="console-stage stack-gap">
    <section v-if="!canViewTasks && !canManualScore" class="console-block">
      <el-empty description="当前账号没有阅卷模块权限" />
    </section>

    <section v-if="canViewTasks" class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">阅卷任务池</h3>
          <p class="block-sub">筛选任务状态后，点击“开始评分”进入人工评分流程。</p>
        </div>
      </div>

      <div class="query-row filters">
        <el-select v-model="gradingStatus" clearable placeholder="按状态筛选">
          <el-option label="待人工评分" value="MANUAL_REQUIRED" />
          <el-option label="自动批改完成" value="AUTO_DONE" />
          <el-option label="已完成" value="DONE" />
        </el-select>
        <el-select
          v-model="gradingPaperId"
          clearable
          placeholder="按试卷筛选"
          :disabled="!paperFilterOptions.length"
        >
          <el-option v-for="item in paperFilterOptions" :key="item.paperId" :label="item.label" :value="item.paperId" />
        </el-select>
        <el-button :loading="loading.list" @click="loadGradingTasks">查询任务</el-button>
        <el-button
          v-if="canLoadExamDirectory || canLoadPaperDirectory"
          :loading="loading.publishedExams || loading.papers"
          @click="refreshPaperFilters"
        >
          刷新试卷
        </el-button>
        <el-button v-if="canLoadUserDirectory" :loading="loading.users" @click="loadStudentDirectory({ force: true })">
          刷新姓名
        </el-button>
      </div>

      <el-table :data="displayedGradingTasks" size="small" max-height="280">
        <template #empty>
          <el-empty :image-size="70" description="当前无任务；若试卷仅含客观题，可切换筛选为“自动批改完成”" />
        </template>
        <el-table-column label="学生姓名" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ resolveStudentName(row) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="140">
          <template #default="{ row }">{{ toStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="objectiveScore" label="客观题得分" width="110" />
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              v-if="canManualScore"
              text
              type="primary"
              :disabled="!isTaskManualRequired(row)"
              @click="fillManualScoreTask(row)"
            >
              {{ isSelectedTask(row) ? '已载入' : '开始评分' }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <p v-if="!canLoadUserDirectory" class="hint-text">当前账号无用户目录权限，暂以学号显示。</p>
      <p v-if="!canLoadExamDirectory" class="hint-text">当前账号无考试目录权限，暂不支持试卷筛选。</p>
      <p v-if="!canLoadPaperDirectory" class="hint-text">当前账号无试卷目录权限，筛选项将显示试卷ID。</p>
    </section>

    <section v-if="canManualScore" class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">人工评分工作区</h3>
          <p class="block-sub">只需填写分数和评语，提交后系统自动发布最终成绩事件。</p>
        </div>
      </div>

      <el-alert
        v-if="!selectedTask"
        title="请先在上方任务列表点击“开始评分”"
        type="info"
        :closable="false"
        show-icon
      />

      <el-form v-else label-position="top">
        <div class="metrics-grid cols-3">
          <article class="metric-card">
            <span>评分对象</span>
            <strong>{{ resolveStudentName(selectedTask) }}</strong>
          </article>
          <article class="metric-card">
            <span>主观题数量</span>
            <strong>{{ subjectiveQuestionCount }}</strong>
          </article>
          <article class="metric-card">
            <span>客观题得分</span>
            <strong>{{ selectedTask.objectiveScore ?? '-' }}</strong>
          </article>
        </div>

        <el-form-item label="评分明细">
          <div class="dynamic-list">
            <div v-for="(item, index) in manualScoreForm.scores" :key="item.questionId || index" class="manual-row">
              <div class="question-meta">
                <strong>{{ item.displayLabel }}</strong>
                <span class="hint-text">满分：{{ item.maxScore ?? '-' }} 分</span>
              </div>
              <el-input-number
                v-model="item.gotScore"
                :min="0"
                :max="item.maxScore ?? undefined"
                :step="1"
                controls-position="right"
              />
              <el-input v-model="item.comment" placeholder="评语（可选）" />
            </div>
          </div>
        </el-form-item>

        <div class="action-row">
          <el-button
            type="primary"
            :loading="loading.manual"
            :disabled="!subjectiveQuestionCount"
            @click="submitManualScore"
          >
            提交人工评分
          </el-button>
        </div>
      </el-form>
    </section>

    <section v-if="canManualScore" class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">评分结果</h3>
      </div>
      <el-alert
        v-if="manualScoreResult"
        title="评分已提交，成绩正在推送到报表（通常几秒内可见）"
        type="success"
        :closable="false"
        show-icon
      />
      <el-empty v-else :image-size="70" description="提交评分后，将在这里显示结果提示" />
    </section>
  </div>
</template>

<style scoped>
.query-row.filters {
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) auto auto auto;
}

.manual-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) auto minmax(200px, 2fr);
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.question-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

@media (max-width: 1080px) {
  .query-row.filters {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 920px) {
  .manual-row {
    grid-template-columns: 1fr;
  }
}
</style>
