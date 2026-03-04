<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { prettyJson, useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

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

const addOption = () => {
  const nextKey = String.fromCharCode(65 + form.options.length)
  form.options.push({ key: nextKey, text: '' })
}

const removeOption = (index) => {
  if (form.options.length <= 1) return
  form.options.splice(index, 1)
}

const listQuestions = async () => {
  const data = await run('list', () => api.listQuestions())
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

  const data = await run('create', () => api.createQuestion(payload), { successMessage: '题目创建成功' })
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
  const data = await run('detail', () => api.getQuestion(questionId))
  if (data) {
    questionDetail.value = data
  }
}
</script>

<template>
  <div class="stage-layout two-panels">
    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">创建题目</h3>
          <p class="block-sub">在题库中登记标准答案和题目元数据。</p>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item label="题型">
          <el-radio-group v-model="form.type" size="small">
            <el-radio-button v-for="type in questionTypes" :key="type" :label="type">{{ type }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <div class="form-grid cols-2">
          <el-form-item label="难度 1-5">
            <el-input-number v-model="form.difficulty" :min="1" :max="5" />
          </el-form-item>
          <el-form-item label="知识点">
            <el-input v-model="form.knowledgePoint" placeholder="例如：代数方程" />
          </el-form-item>
        </div>

        <el-form-item label="题干">
          <el-input v-model="form.stem" type="textarea" :rows="3" placeholder="请输入完整题干" />
        </el-form-item>

        <el-form-item v-if="isChoiceQuestion" label="选项配置">
          <div class="dynamic-list">
            <div v-for="(option, index) in form.options" :key="index" class="option-row">
              <el-input v-model="option.key" class="option-key" placeholder="A" />
              <el-input v-model="option.text" placeholder="选项内容" />
              <el-button text type="danger" @click="removeOption(index)">删除</el-button>
            </div>
            <el-button text type="primary" @click="addOption">新增选项</el-button>
          </div>
        </el-form-item>

        <el-form-item label="标准答案">
          <el-input v-model="form.answer" placeholder="单选示例 A，多选示例 A,B，判断示例 true" />
        </el-form-item>

        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>

        <div class="action-row">
          <el-button type="primary" :loading="loading.create" @click="createQuestion">创建题目</el-button>
          <el-button :loading="loading.list" @click="listQuestions">刷新列表</el-button>
        </div>
      </el-form>
    </section>

    <section class="stack-gap">
      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">题目检索</h3>
        </div>

        <div class="query-row">
          <el-input v-model="questionDetailId" placeholder="输入题目 ID" />
          <el-button :loading="loading.detail" @click="getQuestion">查询</el-button>
        </div>

        <el-table :data="questionList" size="small" max-height="260">
          <el-table-column prop="id" label="ID" min-width="150" />
          <el-table-column prop="type" label="类型" width="90" />
          <el-table-column prop="difficulty" label="难度" width="75" />
          <el-table-column prop="stem" label="题干" min-width="240" show-overflow-tooltip />
        </el-table>
      </section>

      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">结果预览</h3>
        </div>
        <pre class="json-block">{{ prettyJson(questionDetail) }}</pre>
      </section>
    </section>
  </div>
</template>

<style scoped>
.option-row {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.option-key {
  max-width: 90px;
}

@media (max-width: 840px) {
  .option-row {
    grid-template-columns: 1fr;
  }

  .option-key {
    max-width: none;
  }
}
</style>
