<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../../api/client'
import { prettyJson, useAsyncAction } from '../../../composables/useAsyncAction'

const router = useRouter()
const { loading, run } = useAsyncAction()

const questionTypeOptions = [
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTI', label: '多选题' },
  { value: 'JUDGE', label: '判断题' },
  { value: 'FILL', label: '填空题' },
  { value: 'SHORT', label: '简答题' },
]

const createDefaultChoiceOptions = () => [
  { key: 'A', text: '' },
  { key: 'B', text: '' },
  { key: 'C', text: '' },
  { key: 'D', text: '' },
]

const createDefaultForm = () => ({
  type: 'SINGLE',
  stem: '',
  options: createDefaultChoiceOptions(),
  answer: '',
  difficulty: 3,
  knowledgePoint: '',
  analysis: '',
})

const form = reactive(createDefaultForm())
const createdQuestion = ref(null)

const isChoiceQuestion = computed(() => ['SINGLE', 'MULTI'].includes(form.type))

watch(
  () => form.type,
  (type) => {
    if (isChoiceQuestion.value && !form.options.length) {
      form.options = createDefaultChoiceOptions()
    }
    if (!isChoiceQuestion.value) {
      form.options = []
    }
    if (type === 'JUDGE' && !['true', 'false'].includes(String(form.answer).trim().toLowerCase())) {
      form.answer = 'true'
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

const resetForm = () => {
  Object.assign(form, createDefaultForm())
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
    if (payload.options.length < 2) {
      ElMessage.warning('选择题至少需要两个有效选项')
      return
    }
  }

  const data = await run('create', () => api.createQuestion(payload), { successMessage: '题目创建成功' })
  if (data) {
    createdQuestion.value = data
  }
}

const goToLibrary = () => {
  router.push('/questions/library')
}
</script>

<template>
  <div class="stage-layout two-panels">
    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">创建题目</h3>
          <p class="block-sub">专注录题流程，减少与检索逻辑耦合。</p>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item label="题型">
          <el-radio-group v-model="form.type" size="small">
            <el-radio-button v-for="item in questionTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <div class="form-grid cols-2">
          <el-form-item label="难度 1-5">
            <el-input-number v-model="form.difficulty" :min="1" :max="5" />
          </el-form-item>
          <el-form-item label="知识点">
            <el-input v-model="form.knowledgePoint" placeholder="例如：Java 并发" />
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
          <el-radio-group v-if="form.type === 'JUDGE'" v-model="form.answer" size="small">
            <el-radio-button value="true">true</el-radio-button>
            <el-radio-button value="false">false</el-radio-button>
          </el-radio-group>
          <el-input
            v-else
            v-model="form.answer"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            placeholder="单选示例 A，多选示例 A,B，填空或简答请输入文本答案"
          />
        </el-form-item>

        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>

        <div class="action-row">
          <el-button type="primary" :loading="loading.create" @click="createQuestion">创建题目</el-button>
          <el-button @click="resetForm">重置表单</el-button>
          <el-button @click="goToLibrary">去检索页</el-button>
        </div>
      </el-form>
    </section>

    <section class="stack-gap">
      <section class="console-block">
        <div class="block-head compact">
          <h3 class="block-title">创建结果</h3>
        </div>
        <pre class="json-block">{{ prettyJson(createdQuestion) }}</pre>
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
