<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'

const loading = reactive({})
const form = reactive({
  name: '',
  timeLimitMinutes: 90,
  questions: [{ questionId: '', score: 10, orderNo: 1 }],
})
const paperDetailId = ref('')
const paperDetail = ref(null)

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
  const data = await execute('create', () => api.createPaper(payload), '试卷创建成功')
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
  const data = await execute('detail', () => api.getPaper(paperId))
  if (data) {
    paperDetail.value = data
  }
}
</script>

<template>
  <el-form label-position="top">
    <el-row :gutter="12">
      <el-col :xs="24" :md="14">
        <el-form-item label="试卷名称">
          <el-input v-model="form.name" />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="form.timeLimitMinutes" :min="10" :step="10" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item label="题目清单">
      <div class="dynamic-list">
        <div v-for="(item, index) in form.questions" :key="index" class="dynamic-row">
          <el-input v-model="item.questionId" placeholder="questionId" />
          <el-input-number v-model="item.score" :min="1" :step="1" />
          <el-input-number v-model="item.orderNo" :min="1" :step="1" />
          <el-button link type="danger" @click="removeQuestion(index)">删除</el-button>
        </div>
        <el-button link type="primary" @click="addQuestion">新增题目</el-button>
      </div>
    </el-form-item>
    <el-button type="primary" :loading="loading.create" @click="createPaper">创建试卷</el-button>
  </el-form>

  <div class="inline-query">
    <el-input v-model="paperDetailId" placeholder="输入试卷 ID 查询" />
    <el-button :loading="loading.detail" @click="getPaper">查询</el-button>
  </div>

  <pre class="json-block">{{ toPretty(paperDetail) }}</pre>
</template>
