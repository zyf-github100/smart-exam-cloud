<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'

const loading = reactive({})
const questionTypes = ['SINGLE', 'MULTI', 'JUDGE', 'FILL', 'SHORT']
const form = reactive({
  type: 'SINGLE',
  stem: '',
  options: [
    { key: 'A', text: '' },
    { key: 'B', text: '' },
  ],
  answer: '',
  difficulty: 3,
  knowledgePoint: '',
  analysis: '',
})
const questionList = ref([])
const questionDetailId = ref('')
const questionDetail = ref(null)

const isChoiceQuestion = computed(() => ['SINGLE', 'MULTI'].includes(form.type))

watch(
  () => form.type,
  (type) => {
    if (['SINGLE', 'MULTI'].includes(type) && !form.options.length) {
      form.options = [
        { key: 'A', text: '' },
        { key: 'B', text: '' },
      ]
    }
    if (!['SINGLE', 'MULTI'].includes(type)) {
      form.options = []
    }
  }
)

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

const addOption = () => {
  const nextKey = String.fromCharCode(65 + form.options.length)
  form.options.push({ key: nextKey, text: '' })
}

const removeOption = (index) => {
  if (form.options.length <= 1) return
  form.options.splice(index, 1)
}

const listQuestions = async () => {
  const data = await execute('list', () => api.listQuestions())
  if (data) {
    questionList.value = Array.isArray(data) ? data : []
  }
}

const createQuestion = async () => {
  if (!form.stem.trim() || !form.answer.trim()) {
    ElMessage.warning('题干和答案不能为空')
    return
  }
  const payload = {
    type: form.type,
    stem: form.stem.trim(),
    answer: form.answer.trim(),
    difficulty: Number(form.difficulty),
    knowledgePoint: form.knowledgePoint.trim(),
    analysis: form.analysis.trim(),
  }
  if (isChoiceQuestion.value) {
    payload.options = form.options
      .map((item) => ({ key: item.key.trim(), text: item.text.trim() }))
      .filter((item) => item.key && item.text)
  }
  const data = await execute('create', () => api.createQuestion(payload), '题目创建成功')
  if (data) {
    questionDetail.value = data
    questionDetailId.value = data.id
    await listQuestions()
  }
}

const getQuestion = async () => {
  const questionId = questionDetailId.value.trim()
  if (!questionId) {
    ElMessage.warning('请输入题目 ID')
    return
  }
  const data = await execute('detail', () => api.getQuestion(questionId))
  if (data) {
    questionDetail.value = data
  }
}
</script>

<template>
  <el-form label-position="top">
    <el-row :gutter="12">
      <el-col :xs="24" :md="8">
        <el-form-item label="题型">
          <el-select v-model="form.type">
            <el-option v-for="type in questionTypes" :key="type" :label="type" :value="type" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-form-item label="难度 1-5">
          <el-input-number v-model="form.difficulty" :min="1" :max="5" />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-form-item label="知识点">
          <el-input v-model="form.knowledgePoint" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-form-item label="题干">
      <el-input v-model="form.stem" type="textarea" :rows="2" />
    </el-form-item>
    <el-form-item v-if="isChoiceQuestion" label="选项">
      <div class="dynamic-list">
        <div v-for="(option, index) in form.options" :key="index" class="dynamic-row">
          <el-input v-model="option.key" placeholder="Key" class="small-input" />
          <el-input v-model="option.text" placeholder="Text" />
          <el-button link type="danger" @click="removeOption(index)">删除</el-button>
        </div>
        <el-button link type="primary" @click="addOption">新增选项</el-button>
      </div>
    </el-form-item>
    <el-form-item label="标准答案">
      <el-input v-model="form.answer" placeholder="单选示例 A，多选示例 A,B，判断示例 true" />
    </el-form-item>
    <el-form-item label="解析">
      <el-input v-model="form.analysis" type="textarea" :rows="2" />
    </el-form-item>
    <el-space wrap>
      <el-button type="primary" :loading="loading.create" @click="createQuestion">创建题目</el-button>
      <el-button :loading="loading.list" @click="listQuestions">刷新题目列表</el-button>
    </el-space>
  </el-form>

  <div class="inline-query">
    <el-input v-model="questionDetailId" placeholder="输入题目 ID 查询" />
    <el-button :loading="loading.detail" @click="getQuestion">查询</el-button>
  </div>

  <el-table :data="questionList" size="small" max-height="220">
    <el-table-column prop="id" label="ID" min-width="150" />
    <el-table-column prop="type" label="类型" width="90" />
    <el-table-column prop="difficulty" label="难度" width="75" />
    <el-table-column prop="stem" label="题干" min-width="240" show-overflow-tooltip />
  </el-table>

  <pre class="json-block">{{ toPretty(questionDetail) }}</pre>
</template>
