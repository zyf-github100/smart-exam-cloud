<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AUTH_CHANGED_EVENT, getSavedUser } from '../../../api/client'
import { hasAnyPermission } from '../../../composables/accessControl'
import { usePaperComposerWorkspace } from '../../../composables/usePaperComposerWorkspace'

const router = useRouter()
const authVersion = ref(0)
const bankTableRef = ref(null)

const {
  questionTypeOptions,
  bankFilters,
  bankPagination,
  questionBank,
  questionBankTotal,
  questionBankBaseTotal,
  pendingSelectionMap,
  questionBankLoading,
  form,
  knowledgePointOptions,
  pagedQuestionBank,
  pendingSelectionList,
  pendingSelectionCount,
  renderTypeLabel,
  getQuestionType,
  getQuestionStem,
  addQuestionFromBank,
  addPendingQuestionsToPaper,
  replacePendingSelectionByPage,
  addToPendingFromRow,
  removePendingQuestion,
  clearPendingSelection,
  setComposeStep,
  loadQuestionBank,
  loadKnowledgePointOptions,
} = usePaperComposerWorkspace()

const currentUser = computed(() => {
  authVersion.value
  return getSavedUser()
})

const canComposePaper = computed(() => hasAnyPermission(currentUser.value, ['PAPER_CREATE']))
const canBrowseQuestionBank = computed(() =>
  hasAnyPermission(currentUser.value, ['QUESTION_LIST', 'QUESTION_DETAIL'])
)
const canOpenPicker = computed(() => canComposePaper.value && canBrowseQuestionBank.value)
const selectedQuestionPreview = computed(() => form.questions.slice(0, 6))
const bankInventoryTotal = computed(() => questionBankBaseTotal.value || questionBankTotal.value)
const bankResultTotal = computed(() => questionBankTotal.value)
const returnActionLabel = computed(() =>
  pendingSelectionCount.value > 0 ? `加入并返回（${pendingSelectionCount.value}）` : '直接返回组卷'
)

let questionBankLoadTimer = null

watch(pagedQuestionBank, async () => {
  await syncTableSelection()
})

watch(
  () => [bankFilters.keyword, bankFilters.type, bankFilters.knowledgePoint],
  async ([, nextType], [, previousType]) => {
    if (!canOpenPicker.value) return
    if (nextType !== previousType) {
      await loadKnowledgePointOptions({ force: true })
    }
    if (bankPagination.pageNo !== 1) {
      bankPagination.pageNo = 1
      return
    }
    scheduleQuestionBankLoad()
  }
)

watch(
  () => bankPagination.pageNo,
  async (nextPage, previousPage) => {
    if (!canOpenPicker.value || nextPage === previousPage) return
    await loadQuestionBank()
  }
)

watch(
  () => bankPagination.pageSize,
  async (nextSize, previousSize) => {
    if (!canOpenPicker.value || nextSize === previousSize) return
    if (bankPagination.pageNo !== 1) {
      bankPagination.pageNo = 1
      return
    }
    await loadQuestionBank()
  }
)

const onAuthChanged = () => {
  authVersion.value += 1
}

const returnToWorkspace = () => {
  router.push({ name: 'papers' })
}

const scheduleQuestionBankLoad = () => {
  if (questionBankLoadTimer) {
    window.clearTimeout(questionBankLoadTimer)
  }
  questionBankLoadTimer = window.setTimeout(() => {
    loadQuestionBank()
  }, 250)
}

const syncTableSelection = async () => {
  await nextTick()
  const table = bankTableRef.value
  if (!table) return
  table.clearSelection()
  pagedQuestionBank.value.forEach((row) => {
    if (pendingSelectionMap.value.has(String(row.id))) {
      table.toggleRowSelection(row, true)
    }
  })
}

const handleBankSelectionChange = (rows) => {
  replacePendingSelectionByPage(pagedQuestionBank.value, Array.isArray(rows) ? rows : [])
}

const handlePendingQuestion = async (row) => {
  addToPendingFromRow(row)
  await syncTableSelection()
}

const handleRemovePendingQuestion = async (questionId) => {
  removePendingQuestion(questionId)
  await syncTableSelection()
}

const handleClearPendingSelection = async () => {
  clearPendingSelection()
  await syncTableSelection()
}

const handleDirectAddQuestion = (row) => {
  const added = addQuestionFromBank(row)
  if (added) {
    ElMessage.success('题目已加入试卷')
  }
}

const handleAddSelectedQuestions = async (returnAfterAdd = false) => {
  if (returnAfterAdd && pendingSelectionCount.value === 0) {
    returnToWorkspace()
    return
  }
  const added = addPendingQuestionsToPaper()
  await syncTableSelection()
  if (added > 0 && returnAfterAdd) {
    returnToWorkspace()
  }
}

const handleRefreshBank = async () => {
  await Promise.all([loadKnowledgePointOptions({ force: true }), loadQuestionBank({ force: true })])
  await syncTableSelection()
}

onMounted(async () => {
  window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
  setComposeStep(1)
  if (canOpenPicker.value) {
    await Promise.all([loadKnowledgePointOptions(), loadQuestionBank()])
    await syncTableSelection()
  }
})

onBeforeUnmount(() => {
  if (questionBankLoadTimer) {
    window.clearTimeout(questionBankLoadTimer)
  }
  window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged)
})
</script>

<template>
  <div class="stack-gap">
    <section class="console-block picker-hero">
      <div class="block-head picker-hero-head">
        <div>
          <h3 class="block-title">独立选题界面</h3>
          <p class="block-sub">在更宽的工作区里筛选、暂存和批量加入题目，处理完成后直接返回组卷。</p>
        </div>
        <div class="action-row">
          <el-button @click="returnToWorkspace">返回组卷</el-button>
          <el-button type="primary" @click="handleAddSelectedQuestions(true)">
            {{ returnActionLabel }}
          </el-button>
        </div>
      </div>

      <div class="metrics-grid cols-4">
        <article class="metric-card">
          <span>题库总量</span>
          <strong>{{ bankInventoryTotal }}</strong>
        </article>
        <article class="metric-card">
          <span>当前筛选结果</span>
          <strong>{{ bankResultTotal }}</strong>
        </article>
        <article class="metric-card">
          <span>暂存待加入</span>
          <strong>{{ pendingSelectionCount }}</strong>
        </article>
        <article class="metric-card">
          <span>试卷已选题</span>
          <strong>{{ form.questions.length }}</strong>
        </article>
      </div>
    </section>

    <section v-if="!canOpenPicker" class="console-block">
      <el-empty description="当前账号没有从题库选题的权限" />
      <div class="action-row">
        <el-button @click="returnToWorkspace">返回组卷</el-button>
      </div>
    </section>

    <div v-else class="picker-layout">
      <section class="console-block picker-main">
        <div class="block-head compact">
          <div>
            <h3 class="block-title">题库筛选与勾选</h3>
            <p class="block-sub">题目列表改为服务端分页，只查询当前页，筛选时不再全量拉取题库。</p>
          </div>
          <el-button :loading="questionBankLoading" @click="handleRefreshBank">刷新题库</el-button>
        </div>

        <div class="picker-filter-grid">
          <el-input v-model="bankFilters.keyword" clearable placeholder="关键词（ID/题干/知识点）" />
          <el-select v-model="bankFilters.type" clearable placeholder="题型筛选">
            <el-option
              v-for="item in questionTypeOptions"
              :key="item.value || 'ALL'"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="bankFilters.knowledgePoint" clearable filterable placeholder="知识点筛选">
            <el-option v-for="item in knowledgePointOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>

        <div class="action-row picker-toolbar">
          <el-button type="primary" plain :disabled="!pendingSelectionCount" @click="handleAddSelectedQuestions(false)">
            加入勾选题目
          </el-button>
          <span class="hint-text">当前可选 {{ bankResultTotal }} 题，已暂存 {{ pendingSelectionCount }} 题</span>
        </div>

        <div class="adaptive-table-wrap">
          <el-table
            ref="bankTableRef"
            row-key="id"
            :data="pagedQuestionBank"
            size="small"
            max-height="620"
            class="adaptive-table"
            @selection-change="handleBankSelectionChange"
          >
            <el-table-column type="selection" width="50" />
            <el-table-column prop="id" label="ID" min-width="150" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ renderTypeLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column prop="difficulty" label="难度" width="80" />
            <el-table-column prop="knowledgePoint" label="知识点" min-width="120" show-overflow-tooltip />
            <el-table-column prop="stem" label="题干" min-width="320" show-overflow-tooltip />
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link @click="handlePendingQuestion(row)">暂存</el-button>
                  <el-button link type="primary" @click="handleDirectAddQuestion(row)">直接加入</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-pagination
          class="pagination-row"
          background
          layout="prev, pager, next, sizes, total"
          :current-page="bankPagination.pageNo"
          :page-size="bankPagination.pageSize"
          :page-sizes="[10, 20, 30, 50]"
          :total="bankResultTotal"
          @update:current-page="(val) => (bankPagination.pageNo = val)"
          @update:page-size="(val) => ((bankPagination.pageSize = val), (bankPagination.pageNo = 1))"
        />
      </section>

      <section class="console-block picker-side">
        <div class="side-section">
          <div class="pending-head">
            <h4>待加入列表</h4>
            <span>{{ pendingSelectionCount }} 题</span>
          </div>

          <div class="action-row pending-actions">
            <el-button type="primary" :disabled="!pendingSelectionCount" @click="handleAddSelectedQuestions(false)">
              批量加入试卷
            </el-button>
            <el-button :disabled="!pendingSelectionCount" @click="handleClearPendingSelection">清空暂存</el-button>
          </div>

          <el-empty v-if="!pendingSelectionCount" :image-size="70" description="暂存区为空，先在左侧勾选或暂存题目" />

          <div v-else class="pending-list">
            <article v-for="item in pendingSelectionList" :key="item.id" class="pending-item">
              <div class="pending-item-head">
                <strong>{{ renderTypeLabel(item.type) }}</strong>
                <el-button text type="danger" @click="handleRemovePendingQuestion(item.id)">移除</el-button>
              </div>
              <p class="pending-meta">ID: {{ item.id }} · 难度: {{ item.difficulty || '-' }}</p>
              <p class="pending-stem">{{ item.stem || '-' }}</p>
            </article>
          </div>
        </div>

        <div class="side-section">
          <div class="pending-head">
            <h4>当前试卷预览</h4>
            <span>{{ form.questions.length }} 题</span>
          </div>
          <p class="hint-text">返回组卷页后，还可以继续调整顺序、分值和题型分组。</p>

          <el-empty v-if="!form.questions.length" :image-size="70" description="当前试卷还没有已加入题目" />

          <div v-else class="current-paper-list">
            <article v-for="item in selectedQuestionPreview" :key="`${item.questionId}-${item.orderNo}`" class="current-paper-item">
              <div class="current-paper-head">
                <strong>{{ renderTypeLabel(getQuestionType(item.questionId, item.type)) }}</strong>
                <span>{{ item.score }} 分</span>
              </div>
              <p class="pending-meta">题目 ID: {{ item.questionId }}</p>
              <p class="pending-stem">{{ getQuestionStem(item.questionId, item.stem) }}</p>
            </article>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.picker-hero-head {
  align-items: center;
}

.picker-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 0.9fr);
  gap: 14px;
}

.picker-filter-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) repeat(2, minmax(180px, 0.8fr));
  gap: 10px;
  margin-top: 10px;
  margin-bottom: 10px;
}

.picker-toolbar {
  margin-bottom: 10px;
  align-items: center;
}

.picker-side {
  display: grid;
  gap: 12px;
  align-content: start;
}

.side-section {
  border: 1px solid rgba(204, 220, 208, 0.95);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(248, 252, 249, 0.96), rgba(242, 249, 244, 0.92));
  padding: 14px;
}

.pending-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.pending-head h4 {
  margin: 0;
  font-size: 15px;
  color: #244535;
}

.pending-head span {
  font-size: 12px;
  color: var(--ink-soft);
}

.pending-actions {
  margin-bottom: 12px;
}

.adaptive-table-wrap {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 2px;
  -webkit-overflow-scrolling: touch;
}

.adaptive-table-wrap :deep(.adaptive-table) {
  min-width: 1040px;
}

.pagination-row {
  margin-top: 8px;
  justify-content: flex-end;
}

.row-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  white-space: nowrap;
}

.pending-list,
.current-paper-list {
  display: grid;
  gap: 10px;
  max-height: 540px;
  overflow: auto;
  padding-right: 4px;
}

.pending-item,
.current-paper-item {
  border: 1px solid rgba(203, 218, 207, 0.92);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.84);
  padding: 10px 12px;
}

.pending-item-head,
.current-paper-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.current-paper-head span {
  color: var(--ink-soft);
  font-size: 12px;
}

.pending-meta,
.pending-stem {
  margin: 0;
  color: var(--ink-soft);
  line-height: 1.5;
}

.pending-stem {
  margin-top: 4px;
  color: var(--ink-main);
}

@media (max-width: 1180px) {
  .picker-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 920px) {
  .picker-hero-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .picker-filter-grid {
    grid-template-columns: 1fr;
  }

  .pagination-row {
    justify-content: flex-start;
  }
}
</style>
