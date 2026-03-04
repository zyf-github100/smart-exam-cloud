<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api/client'
import { prettyJson, useAsyncAction } from '../../composables/useAsyncAction'

const { loading, run } = useAsyncAction()

const gradingStatus = ref('')
const gradingTasks = ref([])
const manualScoreForm = reactive({
  taskId: '',
  scores: [{ questionId: '', gotScore: 0, comment: '' }],
})
const manualScoreResult = ref(null)

const loadGradingTasks = async () => {
  const data = await run('list', () => api.listGradingTasks(gradingStatus.value))
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

  const data = await run('manual', () => api.manualScore(taskId, { scores }), {
    successMessage: '人工评分提交成功',
  })

  if (data) {
    manualScoreResult.value = data
    await loadGradingTasks()
  }
}
</script>

<template>
  <div class="console-stage stack-gap">
    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">阅卷任务池</h3>
          <p class="block-sub">筛选任务状态并快速进入人工评分流程。</p>
        </div>
      </div>

      <div class="query-row">
        <el-select v-model="gradingStatus" clearable placeholder="按状态筛选">
          <el-option label="MANUAL_REQUIRED" value="MANUAL_REQUIRED" />
          <el-option label="AUTO_DONE" value="AUTO_DONE" />
          <el-option label="DONE" value="DONE" />
        </el-select>
        <el-button :loading="loading.list" @click="loadGradingTasks">查询任务</el-button>
      </div>

      <el-table :data="gradingTasks" size="small" max-height="260">
        <el-table-column prop="id" label="任务ID" min-width="145" />
        <el-table-column prop="examId" label="考试ID" min-width="130" />
        <el-table-column prop="status" label="状态" width="140" />
        <el-table-column prop="objectiveScore" label="客观分" width="90" />
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button text type="primary" @click="fillManualScoreTask(scope.row)">载入评分</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="console-block">
      <div class="block-head">
        <div>
          <h3 class="block-title">人工评分工作区</h3>
          <p class="block-sub">覆盖主观题得分后，系统会自动发布最终成绩事件。</p>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item label="任务 ID">
          <el-input v-model="manualScoreForm.taskId" />
        </el-form-item>

        <el-form-item label="评分明细">
          <div class="dynamic-list">
            <div v-for="(item, index) in manualScoreForm.scores" :key="index" class="manual-row">
              <el-input v-model="item.questionId" placeholder="questionId" />
              <el-input-number v-model="item.gotScore" :min="0" :step="1" />
              <el-input v-model="item.comment" placeholder="comment" />
              <el-button text type="danger" @click="removeManualScoreRow(index)">删除</el-button>
            </div>
            <el-button text type="primary" @click="addManualScoreRow">新增评分项</el-button>
          </div>
        </el-form-item>

        <div class="action-row">
          <el-button type="primary" :loading="loading.manual" @click="submitManualScore">提交人工评分</el-button>
        </div>
      </el-form>
    </section>

    <section class="console-block">
      <div class="block-head compact">
        <h3 class="block-title">评分结果</h3>
      </div>
      <pre class="json-block">{{ prettyJson(manualScoreResult) }}</pre>
    </section>
  </div>
</template>

<style scoped>
.manual-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) auto minmax(180px, 2fr) auto;
  gap: 8px;
  align-items: center;
}

@media (max-width: 920px) {
  .manual-row {
    grid-template-columns: 1fr;
  }
}
</style>