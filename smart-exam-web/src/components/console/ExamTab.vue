<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'

const loading = reactive({})
const form = reactive({
  paperId: '',
  title: '',
  startTime: '',
  endTime: '',
  antiCheatLevel: 1,
})
const createdExam = ref(null)
const startExamId = ref('')
const startedSession = ref(null)
const answerRows = ref([{ questionId: '', answerContent: '', markedForReview: false }])
const submitSessionId = ref('')
const submitResult = ref(null)

const setLoading = (key, value) => {
  loading[key] = value
}

const execute = async (key, action, successMessage) => {
  setLoading(key, true)
  try {
    const result = await action()
    if (successMessage) {
      ElMessage.success(successMessage)
    }
    return result
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

const formatDateTimeForBackend = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const parseAnswerContent = (raw) => {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  if ((text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']'))) {
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }
  if (text === 'true') return true
  if (text === 'false') return false
  const maybeNumber = Number(text)
  if (!Number.isNaN(maybeNumber) && text !== '') return maybeNumber
  return text
}

const createExam = async () => {
  if (!form.paperId.trim() || !form.title.trim()) {
    ElMessage.warning('请先填写试卷 ID 和考试标题')
    return
  }
  const payload = {
    paperId: form.paperId.trim(),
    title: form.title.trim(),
    startTime: formatDateTimeForBackend(form.startTime),
    endTime: formatDateTimeForBackend(form.endTime),
    antiCheatLevel: Number(form.antiCheatLevel),
  }
  if (!payload.startTime || !payload.endTime) {
    ElMessage.warning('请填写完整的开始和结束时间')
    return
  }
  const data = await execute('createExam', () => api.createExam(payload), '考试创建成功')
  if (data) {
    createdExam.value = data
    startExamId.value = data.id
  }
}

const startExam = async () => {
  const examId = startExamId.value.trim()
  if (!examId) {
    ElMessage.warning('请输入考试 ID')
    return
  }
  const data = await execute('startExam', () => api.startExam(examId), '考试会话已创建')
  if (data) {
    startedSession.value = data
    submitSessionId.value = data.sessionId
  }
}

const addAnswerRow = () => {
  answerRows.value.push({ questionId: '', answerContent: '', markedForReview: false })
}

const removeAnswerRow = (index) => {
  if (answerRows.value.length <= 1) return
  answerRows.value.splice(index, 1)
}

const saveAnswers = async () => {
  const sessionId = String(submitSessionId.value || startedSession.value?.sessionId || '').trim()
  if (!sessionId) {
    ElMessage.warning('请先填写会话 ID')
    return
  }
  const answers = answerRows.value
    .map((item) => ({
      questionId: item.questionId.trim(),
      answerContent: parseAnswerContent(item.answerContent),
      markedForReview: Boolean(item.markedForReview),
    }))
    .filter((item) => item.questionId)
  if (!answers.length) {
    ElMessage.warning('至少填写一条答案')
    return
  }
  await execute('saveAnswers', () => api.saveAnswers(sessionId, { answers }), '答案已保存')
}

const submitSession = async () => {
  const sessionId = String(submitSessionId.value || startedSession.value?.sessionId || '').trim()
  if (!sessionId) {
    ElMessage.warning('请先填写会话 ID')
    return
  }
  const data = await execute('submit', () => api.submitSession(sessionId), '交卷成功')
  if (data) {
    submitResult.value = data
  }
}
</script>

<template>
  <el-form label-position="top">
    <el-row :gutter="12">
      <el-col :xs="24" :md="8">
        <el-form-item label="试卷 ID">
          <el-input v-model="form.paperId" />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-form-item label="考试标题">
          <el-input v-model="form.title" />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-form-item label="防作弊等级">
          <el-input-number v-model="form.antiCheatLevel" :min="1" :max="5" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="12">
      <el-col :xs="24" :md="12">
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-col>
    </el-row>
    <el-button type="primary" :loading="loading.createExam" @click="createExam">创建考试</el-button>
  </el-form>

  <div class="inline-query">
    <el-input v-model="startExamId" placeholder="考试 ID" />
    <el-button type="primary" :loading="loading.startExam" @click="startExam">开始考试</el-button>
  </div>

  <el-form label-position="top">
    <el-form-item label="会话 ID (保存/交卷)">
      <el-input v-model="submitSessionId" placeholder="可直接使用上一步返回的 sessionId" />
    </el-form-item>
    <el-form-item label="答案列表 (answerContent 支持文本或 JSON)">
      <div class="dynamic-list">
        <div v-for="(item, index) in answerRows" :key="index" class="dynamic-row">
          <el-input v-model="item.questionId" placeholder="questionId" />
          <el-input v-model="item.answerContent" placeholder='answerContent, 如 "A" 或 ["A","C"]' />
          <el-switch v-model="item.markedForReview" />
          <el-button link type="danger" @click="removeAnswerRow(index)">删除</el-button>
        </div>
        <el-button link type="primary" @click="addAnswerRow">新增答案</el-button>
      </div>
    </el-form-item>
    <el-space wrap>
      <el-button :loading="loading.saveAnswers" @click="saveAnswers">保存答案</el-button>
      <el-button type="danger" :loading="loading.submit" @click="submitSession">提交试卷</el-button>
    </el-space>
  </el-form>

  <pre class="json-block">{{ toPretty(createdExam) }}</pre>
  <pre class="json-block">{{ toPretty(startedSession) }}</pre>
  <pre class="json-block">{{ toPretty(submitResult) }}</pre>
</template>
