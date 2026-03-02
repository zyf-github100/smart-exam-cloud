<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import ConnectionPanel from './components/console/ConnectionPanel.vue'
import QuestionTab from './components/console/QuestionTab.vue'
import PaperTab from './components/console/PaperTab.vue'
import ExamTab from './components/console/ExamTab.vue'
import GradingTab from './components/console/GradingTab.vue'
import ReportTab from './components/console/ReportTab.vue'

const activeTab = ref('question')
const nowText = ref(new Date().toLocaleString())
let timer = null

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
  <div class="page-shell">
    <header class="hero">
      <div>
        <p class="hero-kicker">Smart Exam Cloud</p>
        <h1>在线考试前端控制台</h1>
        <p class="hero-desc">命题、组卷、开考、阅卷、报表全流程联调页面。</p>
      </div>
      <span class="hero-clock">{{ nowText }}</span>
    </header>

    <el-row :gutter="16" class="workspace">
      <el-col :xs="24" :xl="8">
        <ConnectionPanel />
      </el-col>

      <el-col :xs="24" :xl="16">
        <el-card class="panel-card" shadow="hover">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="题库" name="question">
              <QuestionTab />
            </el-tab-pane>
            <el-tab-pane label="试卷" name="paper">
              <PaperTab />
            </el-tab-pane>
            <el-tab-pane label="考试会话" name="exam">
              <ExamTab />
            </el-tab-pane>
            <el-tab-pane label="阅卷" name="grading">
              <GradingTab />
            </el-tab-pane>
            <el-tab-pane label="分析报表" name="report">
              <ReportTab />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
