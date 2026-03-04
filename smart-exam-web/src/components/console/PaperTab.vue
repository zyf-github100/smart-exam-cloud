<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { prettyJson, useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

const form = reactive({
  name: '',
  timeLimitMinutes: 90,
  questions: [{ questionId: '', score: 10, orderNo: 1 }],
})
const paperDetailId = ref('')
const paperDetail = ref(null)

const estimatedScore = computed(() =>
  form.questions.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
)

const addQuestion = () => {
  form.questions.push({
    questionId: '',
    score: 10,
    orderNo: form.questions.length + 1,
  })
}

const removeQuestion = (index) => {
  if (form.questions.length <= 1) return
  form.questions.splice(index, 1)
}

const createPaper = async () => {
  if (!form.name.trim()) {
    ElMessage.warning('请输入试卷名称')
    return
  }

  const questions = form.questions
    .map((item, index) => ({
      questionId: item.questionId.trim(),
      score: Number(item.score),
      orderNo: Number(item.orderNo) || index + 1,
    }))
    .filter((item) => item.questionId)

  if (!questions.length) {
    ElMessage.warning('至少需要一个有效题目')
    return
  }

  const payload = {
    name: form.name.trim(),
    timeLimitMinutes: Number(form.timeLimitMinutes),
    questions,
  }

  const data = await run('create', () => api.createPaper(payload), { successMessage: '试卷创建成功' })
  if (data) {
    paperDetail.value = data
    paperDetailId.value = data.id
  }
}

const getPaper = async () => {
  const paperId = paperDetailId.value.trim()
  if (!paperId) {
    ElMessage.warning('请输入试卷 ID')
    return
  }
  const data = await run('detail', () => api.getPaper(paperId))
  if (data) {
    paperDetail.value = data
  }
}
</script>

<template>
  <div class="stage-layout two-panels">
    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">创建试卷</h3>
          <p class="block-sub">组合题目、顺序和分值，生成可直接开考的试卷配置。</p>
        </div>
      </div>

      <div class="metrics-grid cols-3">
        <article class="metric-card">
          <span>题目数量</span>
          <strong>{{ form.questions.length }}</strong>
        </article>
        <article class="metric-card">
          <span>预估总分</span>
          <strong>{{ estimatedScore }}</strong>
        </article>
        <article class="metric-card">
          <span>默认时长</span>
          <strong>{{ form.timeLimitMinutes }} min</strong>
        </article>
      </div>

      <el-form label-position="top">
        <div class="form-grid cols-2">
          <el-form-item label="试卷名称">
            <el-input v-model="form.name" placeholder="例如：高数期中 A 卷" />
          </el-form-item>
          <el-form-item label="时长(分钟)">
            <el-input-number v-model="form.timeLimitMinutes" :min="10" :step="10" />
          </el-form-item>
        </div>

        <el-form-item label="题目清单">
          <div class="dynamic-list">
            <div v-for="(item, index) in form.questions" :key="index" class="paper-row">
              <el-input v-model="item.questionId" placeholder="questionId" />
              <el-input-number v-model="item.score" :min="1" :step="1" />
              <el-input-number v-model="item.orderNo" :min="1" :step="1" />
              <el-button text type="danger" @click="removeQuestion(index)">删除</el-button>
            </div>
            <el-button text type="primary" @click="addQuestion">新增题目</el-button>
          </div>
        </el-form-item>

        <div class="action-row">
          <el-button type="primary" :loading="loading.create" @click="createPaper">创建试卷</el-button>
          <el-button :loading="loading.detail" @click="getPaper">查询试卷</el-button>
        </div>
      </el-form>
    </section>

    <section class="stack-gap">
      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">试卷查询</h3>
        </div>
        <div class="query-row">
          <el-input v-model="paperDetailId" placeholder="输入试卷 ID" />
          <el-button :loading="loading.detail" @click="getPaper">查询</el-button>
        </div>
      </section>

      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">试卷详情</h3>
        </div>
        <pre class="json-block">{{ prettyJson(paperDetail) }}</pre>
      </section>
    </section>
  </div>
</template>

<style scoped>
.paper-row {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) auto auto auto;
  gap: 8px;
  align-items: center;
}

@media (max-width: 840px) {
  .paper-row {
    grid-template-columns: 1fr;
  }
}
</style>