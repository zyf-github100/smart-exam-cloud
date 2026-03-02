<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { api } from '../../api/client'

const loading = reactive({})
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

const setLoading = (key, value) => {
  loading[key] = value
}

const execute = async (key, action) => {
  setLoading(key, true)
  try {
    return await action()
  } catch (error) {
    ElMessage.error(error.message || 'Request failed')
    return null
  } finally {
    setLoading(key, false)
  }
}

const toPretty = (value) => {
  if (!value) return '暂无数据'
  return JSON.stringify(value, null, 2)
}

const loadScoreDistribution = async () => {
  const examId = reportExamId.value.trim()
  if (!examId) {
    ElMessage.warning('请输入考试 ID')
    return
  }
  const data = await execute('scoreDistribution', () => api.scoreDistribution(examId))
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
  const data = await execute('accuracyTop', () => api.questionAccuracyTop(examId, top))
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
    color: ['#168aad'],
    tooltip: { trigger: 'axis' },
    grid: { top: 32, left: 28, right: 18, bottom: 26 },
    xAxis: {
      type: 'category',
      data: scoreDistributionData.value.xAxis || [],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#8aa3af' } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#dfe8ec' } },
    },
    series: [
      {
        type: 'bar',
        data: scoreDistributionData.value.series || [],
        barWidth: '45%',
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#168aad' },
            { offset: 1, color: '#34a0a4' },
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
    color: ['#f4a261'],
    tooltip: { trigger: 'axis' },
    grid: { top: 32, left: 28, right: 18, bottom: 26 },
    xAxis: {
      type: 'category',
      data: accuracyTopData.value.xAxis || [],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#8aa3af' } },
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#dfe8ec' } },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: accuracyTopData.value.series || [],
        symbolSize: 8,
        lineStyle: { width: 3 },
        areaStyle: {
          color: 'rgba(244, 162, 97, 0.2)',
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
  <div class="inline-query">
    <el-input v-model="reportExamId" placeholder="考试 ID" />
    <el-input-number v-model="reportTop" :min="1" :max="50" />
    <el-button :loading="loading.scoreDistribution" @click="loadScoreDistribution">分数分布</el-button>
    <el-button :loading="loading.accuracyTop" @click="loadQuestionAccuracyTop">题目正确率 Top</el-button>
  </div>

  <div class="chart-grid">
    <div ref="scoreChartRef" class="chart-box"></div>
    <div ref="accuracyChartRef" class="chart-box"></div>
  </div>

  <pre class="json-block">{{ toPretty(scoreDistributionData) }}</pre>
  <pre class="json-block">{{ toPretty(accuracyTopData) }}</pre>
</template>
