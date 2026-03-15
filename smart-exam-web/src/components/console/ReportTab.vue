<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { AUTH_CHANGED_EVENT, api, getSavedUser } from '../../api/client'
import { hasAnyPermission } from '../../composables/accessControl'
import { useAsyncAction } from '../../composables/useAsyncAction'

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const { loading, run } = useAsyncAction()

const reportExamId = ref('')
const reportExamOptions = ref([])
const reportTop = ref(10)
const scoreSheetKeyword = ref('')
const scoreSheetLimit = ref(200)
const scoreDistributionData = ref(null)
const accuracyTopData = ref(null)
const scoreSheetData = ref(null)
const scoreChartRef = ref(null)
const accuracyChartRef = ref(null)
const authVersion = ref(0)

let scoreChart = null
let accuracyChart = null

const currentUser = computed(() => {
  authVersion.value
  return getSavedUser()
})
const canViewScoreDistribution = computed(() =>
  hasAnyPermission(currentUser.value, ['REPORT_SCORE_DISTRIBUTION_VIEW'])
)
const canViewAccuracyTop = computed(() =>
  hasAnyPermission(currentUser.value, ['REPORT_QUESTION_ACCURACY_VIEW'])
)
const canViewScoreSheet = computed(() => canViewScoreDistribution.value)
const canViewAnyReport = computed(
  () => canViewScoreDistribution.value || canViewAccuracyTop.value || canViewScoreSheet.value
)
const canLoadReportExams = computed(() => hasAnyPermission(currentUser.value, ['EXAM_CREATE']))

watch(scoreDistributionData, async () => {
  await nextTick()
  renderScoreChart()
})

watch(accuracyTopData, async () => {
  await nextTick()
  renderAccuracyChart()
})

const resolveExamId = (exam) => String(exam?.id ?? exam?.examId ?? '').trim()

const toExamStatusText = (status) => {
  const value = String(status || '').toUpperCase()
  if (value === 'NOT_STARTED') return '未开始'
  if (value === 'RUNNING') return '进行中'
  if (value === 'FINISHED') return '已结束'
  return status || '未知'
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

const buildReportExamOptionLabel = (exam) => {
  const title = String(exam?.title || '未命名考试')
  const status = toExamStatusText(exam?.status)
  const range = `${formatDateTimeDisplay(exam?.startTime)} ~ ${formatDateTimeDisplay(exam?.endTime)}`
  return `${title}｜${status}｜${range}`
}

const loadReportExamOptions = async () => {
  if (!canLoadReportExams.value) {
    reportExamOptions.value = []
    return
  }
  const data = await run('reportExams', () => api.listPublishedExams(), {
    errorMessage: '加载考试列表失败，可在下拉框直接输入考试 ID',
  })
  if (!data) return
  const options = (Array.isArray(data) ? data : []).filter((item) => resolveExamId(item))
  reportExamOptions.value = options
  if (!reportExamId.value && options.length) {
    reportExamId.value = resolveExamId(options[0])
  }
}

watch(
  () => [canViewAnyReport.value, canLoadReportExams.value],
  async ([canView, canLoad]) => {
    if (!canView) {
      reportExamOptions.value = []
      reportExamId.value = ''
      return
    }
    if (!canLoad) {
      reportExamOptions.value = []
      return
    }
    await loadReportExamOptions()
  },
  { immediate: true }
)

const getExamIdOrWarn = () => {
  const examId = reportExamId.value.trim()
  if (!examId) {
    ElMessage.warning('请选择考试')
    return null
  }
  return examId
}

const loadScoreDistribution = async () => {
  if (!canViewScoreDistribution.value) {
    ElMessage.warning('当前账号没有分数分布权限')
    return
  }
  const examId = getExamIdOrWarn()
  if (!examId) return
  const data = await run('scoreDistribution', () => api.scoreDistribution(examId))
  if (data) {
    scoreDistributionData.value = data
  }
}

const loadQuestionAccuracyTop = async () => {
  if (!canViewAccuracyTop.value) {
    ElMessage.warning('当前账号没有题目正确率报表权限')
    return
  }
  const examId = getExamIdOrWarn()
  if (!examId) return
  const top = Math.max(1, Number(reportTop.value) || 10)
  const data = await run('accuracyTop', () => api.questionAccuracyTop(examId, top))
  if (data) {
    accuracyTopData.value = data
  }
}

const loadScoreSheet = async () => {
  if (!canViewScoreSheet.value) {
    ElMessage.warning('当前账号没有成绩单权限')
    return
  }
  const examId = getExamIdOrWarn()
  if (!examId) return
  const keyword = scoreSheetKeyword.value.trim()
  const limit = Math.max(1, Math.min(1000, Number(scoreSheetLimit.value) || 200))
  const data = await run('scoreSheet', () =>
    api.scoreSheet(examId, {
      keyword: keyword || undefined,
      limit,
    })
  )
  if (data) {
    scoreSheetData.value = data
  }
}

const renderScoreChart = () => {
  if (!scoreChartRef.value || !scoreDistributionData.value) return
  if (!scoreChart) {
    scoreChart = echarts.init(scoreChartRef.value)
  }
  scoreChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 36, left: 30, right: 20, bottom: 24 },
    xAxis: {
      type: 'category',
      data: scoreDistributionData.value.xAxis || [],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#98a99d' } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#d6e0d8' } },
    },
    series: [
      {
        type: 'bar',
        data: scoreDistributionData.value.series || [],
        barWidth: '44%',
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#239b74' },
            { offset: 1, color: '#3cc293' },
          ]),
        },
      },
    ],
  })
}

const renderAccuracyChart = () => {
  if (!accuracyChartRef.value || !accuracyTopData.value) return
  if (!accuracyChart) {
    accuracyChart = echarts.init(accuracyChartRef.value)
  }
  accuracyChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 36, left: 30, right: 20, bottom: 24 },
    xAxis: {
      type: 'category',
      data: accuracyTopData.value.xAxis || [],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#98a99d' } },
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#d6e0d8' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: accuracyTopData.value.series || [],
        symbolSize: 8,
        lineStyle: { width: 3, color: '#f08a43' },
        itemStyle: { color: '#f08a43' },
        areaStyle: {
          color: 'rgba(240, 138, 67, 0.22)',
        },
      },
    ],
  })
}

const onResize = () => {
  scoreChart?.resize()
  accuracyChart?.resize()
}

const onAuthChanged = () => {
  authVersion.value += 1
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
  scoreChart?.dispose()
  accuracyChart?.dispose()
})
</script>

<template>
  <div class="console-stage stack-gap">
    <section v-if="!canViewAnyReport" class="console-block">
      <el-empty description="当前账号没有报表模块权限" />
    </section>

    <section v-if="canViewAnyReport" class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">报表筛选</h3>
          <p class="block-sub">按考试名称搜索并选择后，可查看分布、题目正确率与学生成绩单。</p>
        </div>
      </div>

      <div class="query-row wide">
        <el-select
          v-model="reportExamId"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="请选择考试（可输入标题搜索，或直接输入考试 ID）"
          style="width: 100%"
        >
          <el-option
            v-for="exam in reportExamOptions"
            :key="resolveExamId(exam)"
            :label="buildReportExamOptionLabel(exam)"
            :value="resolveExamId(exam)"
          />
        </el-select>
        <el-button v-if="canLoadReportExams" :loading="loading.reportExams" @click="loadReportExamOptions">
          刷新考试
        </el-button>
        <el-input-number v-model="reportTop" :min="1" :max="50" />
        <el-button
          v-if="canViewScoreDistribution"
          :loading="loading.scoreDistribution"
          @click="loadScoreDistribution"
        >
          分数分布
        </el-button>
        <el-button v-if="canViewAccuracyTop" :loading="loading.accuracyTop" @click="loadQuestionAccuracyTop">
          正确率 Top
        </el-button>
      </div>

      <p class="hint-text">学生提交试卷或老师提交人工评分后，成绩会自动推送到报表；若暂时为空，请稍候再查。</p>

      <p v-if="!canLoadReportExams" class="hint-text">当前账号不可拉取考试列表，可直接在下拉框输入考试 ID 查询。</p>

      <div v-if="canViewScoreSheet" class="query-row wide score-sheet-query">
        <el-input v-model="scoreSheetKeyword" clearable placeholder="筛选学生（学号/用户名/姓名）" />
        <el-input-number v-model="scoreSheetLimit" :min="1" :max="1000" />
        <el-button :loading="loading.scoreSheet" @click="loadScoreSheet">成绩单</el-button>
      </div>
    </section>

    <section v-if="canViewScoreDistribution || canViewAccuracyTop" class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">可视化结果</h3>
      </div>
      <div class="chart-grid">
        <div v-if="canViewScoreDistribution" class="chart-panel">
          <p class="chart-title">分数分布</p>
          <div ref="scoreChartRef" class="chart-box"></div>
        </div>
        <div v-if="canViewAccuracyTop" class="chart-panel">
          <p class="chart-title">题目正确率 TopN</p>
          <div ref="accuracyChartRef" class="chart-box"></div>
        </div>
      </div>
    </section>

    <section v-if="canViewScoreSheet" class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">学生成绩单</h3>
      </div>

      <el-table :data="scoreSheetData?.records || []" size="small" max-height="420">
        <template #empty>
          <el-empty :image-size="72" description="暂无成绩单数据，请先选择考试并点击“成绩单”" />
        </template>
        <el-table-column prop="rank" label="排名" width="80" />
        <el-table-column prop="userId" label="学生ID" min-width="130" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="totalScore" label="总分" width="100" />
        <el-table-column label="发布时间" min-width="170">
          <template #default="{ row }">{{ formatDateTimeDisplay(row.publishedAt) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.query-row.wide {
  grid-template-columns: minmax(260px, 1fr) auto auto auto auto;
}

.score-sheet-query {
  margin-top: 8px;
  grid-template-columns: minmax(220px, 1fr) auto auto;
}

.chart-panel {
  padding: 10px;
  border: 1px solid rgba(206, 220, 210, 0.95);
  border-radius: 12px;
  background: linear-gradient(180deg, #fafdfa 0%, #f3f8f3 100%);
}

.chart-title {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: #42574a;
}

@media (max-width: 980px) {
  .query-row.wide,
  .score-sheet-query {
    grid-template-columns: 1fr;
  }
}
</style>
