<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { api } from '../../api/client'
import { prettyJson, useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

const reportExamId = ref('')
const reportTop = ref(10)
const scoreDistributionData = ref(null)
const accuracyTopData = ref(null)
const scoreChartRef = ref(null)
const accuracyChartRef = ref(null)

let scoreChart = null
let accuracyChart = null

watch(scoreDistributionData, async () => {
  await nextTick()
  renderScoreChart()
})

watch(accuracyTopData, async () => {
  await nextTick()
  renderAccuracyChart()
})

const loadScoreDistribution = async () => {
  const examId = reportExamId.value.trim()
  if (!examId) {
    ElMessage.warning('请输入考试 ID')
    return
  }
  const data = await run('scoreDistribution', () => api.scoreDistribution(examId))
  if (data) {
    scoreDistributionData.value = data
  }
}

const loadQuestionAccuracyTop = async () => {
  const examId = reportExamId.value.trim()
  if (!examId) {
    ElMessage.warning('请输入考试 ID')
    return
  }
  const top = Math.max(1, Number(reportTop.value) || 10)
  const data = await run('accuracyTop', () => api.questionAccuracyTop(examId, top))
  if (data) {
    accuracyTopData.value = data
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

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  scoreChart?.dispose()
  accuracyChart?.dispose()
})
</script>

<template>
  <div class="console-stage stack-gap">
    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">报表筛选</h3>
          <p class="block-sub">输入考试 ID，拉取分数分布和题目正确率 TopN。</p>
        </div>
      </div>

      <div class="query-row wide">
        <el-input v-model="reportExamId" placeholder="考试 ID" />
        <el-input-number v-model="reportTop" :min="1" :max="50" />
        <el-button :loading="loading.scoreDistribution" @click="loadScoreDistribution">分数分布</el-button>
        <el-button :loading="loading.accuracyTop" @click="loadQuestionAccuracyTop">正确率 Top</el-button>
      </div>
    </section>

    <section class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">可视化结果</h3>
      </div>
      <div class="chart-grid">
        <div class="chart-panel">
          <p class="chart-title">分数分布</p>
          <div ref="scoreChartRef" class="chart-box"></div>
        </div>
        <div class="chart-panel">
          <p class="chart-title">题目正确率 TopN</p>
          <div ref="accuracyChartRef" class="chart-box"></div>
        </div>
      </div>
    </section>

    <section class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">原始数据回显</h3>
      </div>
      <div class="preview-grid cols-2">
        <pre class="json-block">{{ prettyJson(scoreDistributionData) }}</pre>
        <pre class="json-block">{{ prettyJson(accuracyTopData) }}</pre>
      </div>
    </section>
  </div>
</template>

<style scoped>
.query-row.wide {
  grid-template-columns: minmax(180px, 1fr) auto auto auto;
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
  .query-row.wide {
    grid-template-columns: 1fr;
  }
}
</style>