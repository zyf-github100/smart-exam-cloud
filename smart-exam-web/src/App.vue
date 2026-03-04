<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  DataAnalysis,
  Document,
  Files,
  Histogram,
  Notebook,
  Reading,
  Setting,
  TrendCharts,
} from '@element-plus/icons-vue'
import ConnectionPanel from './components/console/ConnectionPanel.vue'
import QuestionTab from './components/console/QuestionTab.vue'
import PaperTab from './components/console/PaperTab.vue'
import ExamTab from './components/console/ExamTab.vue'
import GradingTab from './components/console/GradingTab.vue'
import ReportTab from './components/console/ReportTab.vue'
import AdminTab from './components/console/AdminTab.vue'

const tabs = [
  {
    name: 'question',
    label: '题库中心',
    short: 'Q',
    icon: Reading,
    tagline: '命题与知识点沉淀',
    description: '统一管理题型、答案与难度，构建后续组卷与判题的数据源。',
    guide: ['优先录入标准答案完整的客观题', '题目答案格式与考试作答格式保持一致', '组卷前先在列表确认题目可检索'],
  },
  {
    name: 'paper',
    label: '试卷编排',
    short: 'P',
    icon: Files,
    tagline: '按规则组织题目结构',
    description: '配置时长、题序和分值，输出可直接用于开考的试卷实体。',
    guide: ['保持题目顺序唯一且连续', '总分建议在创建后立即核对', '试卷详情 ID 用于创建考试'],
  },
  {
    name: 'exam',
    label: '考试会话',
    short: 'E',
    icon: Notebook,
    tagline: '开考、作答与交卷管理',
    description: '围绕时间窗管理考试状态，追踪会话、保存答案并触发交卷事件。',
    guide: ['开考前确认 start/end 时间窗口', '会话 ID 建议直接从开始考试返回值复制', '交卷后可立即切到阅卷中心查看任务'],
  },
  {
    name: 'grading',
    label: '阅卷中心',
    short: 'G',
    icon: Document,
    tagline: '自动判分 + 人工补评分',
    description: '消费交卷事件生成任务，处理主观题评分并发布成绩事件。',
    guide: ['先筛选 MANUAL_REQUIRED 任务', '人工评分需覆盖全部主观题', '提交后可到报表页立即查看变化'],
  },
  {
    name: 'report',
    label: '分析报表',
    short: 'R',
    icon: Histogram,
    tagline: '成绩结构洞察',
    description: '查看分数分布和题目正确率，为教学优化与命题调整提供依据。',
    guide: ['先用考试 ID 拉取分布数据', 'TopN 建议从 10 开始观察', '数据不更新时回到阅卷页确认任务已完成'],
  },
  {
    name: 'admin',
    label: '管理员中心',
    short: 'A',
    icon: Setting,
    tagline: '企业级治理与审计',
    description: '统一处理用户治理、角色权限、系统配置和审计日志，强化平台安全和运维可控性。',
    guide: ['先刷新总览确认平台状态', '用户变更时务必填写原因', '所有高风险动作都在审计日志复核'],
  },
]

const tabComponents = {
  question: QuestionTab,
  paper: PaperTab,
  exam: ExamTab,
  grading: GradingTab,
  report: ReportTab,
  admin: AdminTab,
}

const activeTab = ref('question')
const nowText = ref(new Date().toLocaleString())
let timer = null

const activeMeta = computed(() => tabs.find((item) => item.name === activeTab.value) || tabs[0])
const activeComponent = computed(() => tabComponents[activeTab.value] || QuestionTab)
const activeIndex = computed(() => tabs.findIndex((item) => item.name === activeTab.value))
const progressPercent = computed(() => Math.round(((activeIndex.value + 1) / tabs.length) * 100))

const gotoPrev = () => {
  if (activeIndex.value <= 0) return
  activeTab.value = tabs[activeIndex.value - 1].name
}

const gotoNext = () => {
  if (activeIndex.value >= tabs.length - 1) return
  activeTab.value = tabs[activeIndex.value + 1].name
}

onMounted(() => {
  timer = window.setInterval(() => {
    nowText.value = new Date().toLocaleString()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div class="console-shell">
    <div class="bg-orb orb-a"></div>
    <div class="bg-orb orb-b"></div>

    <header class="console-header reveal-up">
      <div class="brand-panel">
        <div class="brand-mark">SE</div>
        <div>
          <p class="brand-kicker">Smart Exam Cloud Console</p>
          <h1 class="brand-title">智能考试运营控制台</h1>
          <p class="brand-sub">统一处理题库、考试、阅卷和分析的全链路业务操作。</p>
        </div>
      </div>

      <div class="header-status">
        <div class="status-card">
          <span>当前时间</span>
          <strong>{{ nowText }}</strong>
        </div>
        <div class="status-card">
          <span>激活模块</span>
          <strong>{{ activeMeta.label }}</strong>
        </div>
        <div class="status-card status-progress">
          <span>流程进度</span>
          <strong>{{ progressPercent }}%</strong>
          <el-progress :percentage="progressPercent" :show-text="false" :stroke-width="6" />
        </div>
      </div>
    </header>

    <main class="console-main reveal-up delay-1">
      <aside class="stage-rail">
        <div class="rail-head">
          <p class="rail-kicker">Workflow</p>
          <h2>业务阶段导航</h2>
        </div>

        <div class="rail-list">
          <button
            v-for="(tab, index) in tabs"
            :key="tab.name"
            type="button"
            class="rail-item"
            :class="{ active: tab.name === activeTab }"
            @click="activeTab = tab.name"
          >
            <span class="rail-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="rail-main">
              <span class="rail-label">{{ tab.label }}</span>
              <span class="rail-tagline">{{ tab.tagline }}</span>
            </span>
            <span class="rail-icon">
              <el-icon><component :is="tab.icon" /></el-icon>
            </span>
          </button>
        </div>
      </aside>

      <section class="workspace-panel">
        <div class="workspace-head">
          <div>
            <p class="workspace-kicker">Current Stage</p>
            <h2>{{ activeMeta.label }}</h2>
            <p class="workspace-desc">{{ activeMeta.description }}</p>
          </div>
          <div class="workspace-nav">
            <el-button plain :disabled="activeIndex <= 0" @click="gotoPrev">上一步</el-button>
            <el-button type="primary" :disabled="activeIndex >= tabs.length - 1" @click="gotoNext">下一步</el-button>
          </div>
        </div>

        <div class="workspace-grid">
          <section class="connection-area">
            <ConnectionPanel />
          </section>

          <section class="operation-area">
            <el-card class="studio-card" shadow="never">
              <template #header>
                <div class="studio-head">
                  <div>
                    <strong>{{ activeMeta.label }}</strong>
                    <p>{{ activeMeta.tagline }}</p>
                  </div>
                  <el-tag effect="dark" type="info">阶段 {{ activeIndex + 1 }} / {{ tabs.length }}</el-tag>
                </div>
              </template>

              <transition name="tab-fade" mode="out-in">
                <component :is="activeComponent" :key="activeTab" />
              </transition>
            </el-card>
          </section>
        </div>
      </section>

      <aside class="guide-rail">
        <el-card class="guide-card" shadow="never">
          <template #header>
            <div class="guide-head">
              <el-icon><TrendCharts /></el-icon>
              <strong>操作建议</strong>
            </div>
          </template>

          <ol class="guide-list">
            <li v-for="(tip, idx) in activeMeta.guide" :key="`${activeMeta.name}-${idx}`">{{ tip }}</li>
          </ol>
        </el-card>

        <el-card class="guide-card" shadow="never">
          <template #header>
            <div class="guide-head">
              <el-icon><DataAnalysis /></el-icon>
              <strong>推荐路径</strong>
            </div>
          </template>
          <p class="guide-path">题库中心 → 试卷编排 → 考试会话 → 阅卷中心 → 分析报表 → 管理员中心</p>
        </el-card>
      </aside>
    </main>
  </div>
</template>
