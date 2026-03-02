<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'

const loading = reactive({})
const gradingStatus = ref('')
const gradingTasks = ref([])
const manualScoreForm = reactive({
  taskId: '',
  scores: [{ questionId: '', gotScore: 0, comment: '' }],
})
const manualScoreResult = ref(null)

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

const loadGradingTasks = async () => {
  const data = await execute('list', () => api.listGradingTasks(gradingStatus.value))
  if (data) {
    gradingTasks.value = Array.isArray(data) ? data : []
  }
}

const fillManualScoreTask = (task) => {
  manualScoreForm.taskId = task.id
  manualScoreForm.scores =
    task.questionScores && task.questionScores.length
      ? task.questionScores
          .filter((item) => !item.objective)
          .map((item) => ({
            questionId: item.questionId || '',
            gotScore: item.gotScore || 0,
            comment: item.comment || '',
          }))
      : [{ questionId: '', gotScore: 0, comment: '' }]
}

const addManualScoreRow = () => {
  manualScoreForm.scores.push({ questionId: '', gotScore: 0, comment: '' })
}

const removeManualScoreRow = (index) => {
  if (manualScoreForm.scores.length <= 1) return
  manualScoreForm.scores.splice(index, 1)
}

const submitManualScore = async () => {
  const taskId = manualScoreForm.taskId.trim()
  if (!taskId) {
    ElMessage.warning('请先填写阅卷任务 ID')
    return
  }
  const scores = manualScoreForm.scores
    .map((item) => ({
      questionId: item.questionId.trim(),
      gotScore: Number(item.gotScore),
      comment: item.comment?.trim() || '',
    }))
    .filter((item) => item.questionId)
  if (!scores.length) {
    ElMessage.warning('至少填写一条评分明细')
    return
  }
  const data = await execute('manual', () => api.manualScore(taskId, { scores }), '人工评分提交成功')
  if (data) {
    manualScoreResult.value = data
    await loadGradingTasks()
  }
}
</script>

<template>
  <div class="inline-query">
    <el-select v-model="gradingStatus" clearable placeholder="按状态筛选">
      <el-option label="MANUAL_REQUIRED" value="MANUAL_REQUIRED" />
      <el-option label="AUTO_DONE" value="AUTO_DONE" />
      <el-option label="DONE" value="DONE" />
    </el-select>
    <el-button :loading="loading.list" @click="loadGradingTasks">查询任务</el-button>
  </div>

  <el-table :data="gradingTasks" size="small" max-height="240">
    <el-table-column prop="id" label="任务ID" min-width="145" />
    <el-table-column prop="examId" label="考试ID" min-width="130" />
    <el-table-column prop="status" label="状态" width="140" />
    <el-table-column prop="objectiveScore" label="客观分" width="90" />
    <el-table-column prop="totalScore" label="总分" width="90" />
    <el-table-column label="操作" width="90">
      <template #default="scope">
        <el-button link type="primary" @click="fillManualScoreTask(scope.row)">给分</el-button>
      </template>
    </el-table-column>
  </el-table>

  <el-form label-position="top">
    <el-form-item label="任务 ID">
      <el-input v-model="manualScoreForm.taskId" />
    </el-form-item>
    <el-form-item label="评分明细">
      <div class="dynamic-list">
        <div v-for="(item, index) in manualScoreForm.scores" :key="index" class="dynamic-row">
          <el-input v-model="item.questionId" placeholder="questionId" />
          <el-input-number v-model="item.gotScore" :min="0" :step="1" />
          <el-input v-model="item.comment" placeholder="comment" />
          <el-button link type="danger" @click="removeManualScoreRow(index)">删除</el-button>
        </div>
        <el-button link type="primary" @click="addManualScoreRow">新增评分项</el-button>
      </div>
    </el-form-item>
    <el-button type="primary" :loading="loading.manual" @click="submitManualScore">提交人工评分</el-button>
  </el-form>

  <pre class="json-block">{{ toPretty(manualScoreResult) }}</pre>
</template>
