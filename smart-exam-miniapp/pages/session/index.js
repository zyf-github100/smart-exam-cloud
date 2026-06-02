const api = require('../../services/api')
const { buildDraftSnapshot, buildSubmitAnswers, countAnswered, isAnswered, mergePaperAndAnswers } = require('../../utils/exam')
const { formatCountdown } = require('../../utils/format')
const { clearAnswerDraft, getAnswerDraft, saveAnswerDraft } = require('../../utils/storage')

const AUTO_SAVE_INTERVAL_MS = 45000

function ensureLogin() {
  const app = getApp()
  if (!app || !app.isLoggedIn || !app.isLoggedIn()) {
    wx.reLaunch({ url: '/pages/login/index' })
    return false
  }
  return true
}

Page({
  data: {
    sessionId: '',
    examId: '',
    hasLoaded: false,
    loading: true,
    saving: false,
    submitting: false,
    dirty: false,
    paperName: '考试作答',
    paperDesc: '',
    totalCount: 0,
    answeredCount: 0,
    totalScore: 0,
    questions: [],
    saveHint: '尚未保存',
    countdownText: '',
    remainingSeconds: 0,
  },

  onLoad(options) {
    if (!ensureLogin()) return
    const sessionId = String(options.sessionId || '').trim()
    const examId = String(options.examId || '').trim()
    const remainingSeconds = Number(options.timeLimitSeconds) || 0
    if (!sessionId) {
      wx.showToast({ title: '缺少考试会话', icon: 'none' })
      wx.navigateBack()
      return
    }

    const app = getApp()
    if (app && typeof app.setActiveSession === 'function') {
      app.setActiveSession(sessionId)
    }

    this.setData({
      sessionId,
      examId,
      remainingSeconds,
      countdownText: remainingSeconds > 0 ? formatCountdown(remainingSeconds) : '',
    })
  },

  onShow() {
    if (!ensureLogin()) return
    if (!this.data.hasLoaded) {
      this.loadPaper()
    }
    this.startCountdown()
    this.startAutoSave()
    this.bindNetworkStatus()
  },

  onHide() {
    this.persistLocalDraft()
    this.reportSwitchScreen()
  },

  onUnload() {
    if (!this.data.submitting) {
      this.persistLocalDraft()
    }
    this.stopCountdown()
    this.stopAutoSave()
    this.unbindNetworkStatus()
    const app = getApp()
    if (app && typeof app.clearActiveSession === 'function') {
      app.clearActiveSession()
    }
  },

  async loadPaper() {
    if (!this.data.sessionId) return

    this.setData({ loading: true })
    try {
      const [paper, answers] = await Promise.all([
        api.getSessionPaper(this.data.sessionId),
        api.getSessionAnswers(this.data.sessionId),
      ])

      const localDraft = getAnswerDraft(this.data.sessionId)
      const questions = mergePaperAndAnswers(paper || {}, answers || [], localDraft)
      const restoredLocalDraft = Boolean(localDraft && Array.isArray(localDraft.questions) && localDraft.questions.length)

      this.setData({
        hasLoaded: true,
        loading: false,
        dirty: restoredLocalDraft,
        paperName: paper && paper.paperName ? paper.paperName : '考试作答',
        paperDesc: `试卷 ID：${paper && paper.paperId ? paper.paperId : '-'} · 限时 ${
          paper && paper.timeLimitMinutes ? paper.timeLimitMinutes : '-'
        } 分钟`,
        questions,
        totalCount: questions.length,
        answeredCount: countAnswered(questions),
        totalScore: Number(paper && paper.totalScore) || 0,
        saveHint: restoredLocalDraft
          ? '已恢复本地草稿，建议先保存'
          : answers && answers.length
            ? '已加载历史答案'
            : '当前暂无已保存答案',
      })
    } catch (error) {
      this.setData({ hasLoaded: false, loading: false })
      wx.showToast({
        title: error && error.message ? error.message : '加载试卷失败',
        icon: 'none',
        duration: 2200,
      })
    }
  },

  startCountdown() {
    this.stopCountdown()
    if (!this.data.remainingSeconds) return
    this.countdownTimer = setInterval(() => {
      const nextValue = Math.max(0, Number(this.data.remainingSeconds) - 1)
      this.setData({
        remainingSeconds: nextValue,
        countdownText: formatCountdown(nextValue),
      })
      if (nextValue > 0) return
      this.stopCountdown()
      this.autoSubmitWhenTimeout()
    }, 1000)
  },

  stopCountdown() {
    if (!this.countdownTimer) return
    clearInterval(this.countdownTimer)
    this.countdownTimer = null
  },

  startAutoSave() {
    this.stopAutoSave()
    this.autoSaveTimer = setInterval(async () => {
      if (!this.data.dirty || this.data.saving || this.data.submitting || !this.data.questions.length) return
      try {
        await this.saveAnswers({ silent: true, auto: true })
      } catch (error) {
        // 自动保存失败时保留本地草稿，不打断答题。
      }
    }, AUTO_SAVE_INTERVAL_MS)
  },

  stopAutoSave() {
    if (!this.autoSaveTimer) return
    clearInterval(this.autoSaveTimer)
    this.autoSaveTimer = null
  },

  bindNetworkStatus() {
    if (this.networkStatusHandler) return
    this.networkStatusHandler = (result) => {
      if (!result || result.isConnected !== false) return
      this.reportAntiCheatEvent('NETWORK_DISCONNECT', {
        source: 'miniapp.network',
      })
    }
    wx.onNetworkStatusChange(this.networkStatusHandler)
  },

  unbindNetworkStatus() {
    if (!this.networkStatusHandler || !wx.offNetworkStatusChange) return
    wx.offNetworkStatusChange(this.networkStatusHandler)
    this.networkStatusHandler = null
  },

  updateQuestion(index, nextPatch) {
    const questions = (this.data.questions || []).slice()
    if (!questions[index]) return
    const nextQuestion = {
      ...questions[index],
      ...nextPatch,
    }
    nextQuestion.answered = isAnswered(nextQuestion.type, nextQuestion.draftAnswer)
    questions[index] = nextQuestion
    const questionPath = `questions[${index}]`
    this.setData({
      [questionPath]: nextQuestion,
      answeredCount: countAnswered(questions),
      dirty: true,
      saveHint: '有未保存修改，本地草稿已保留',
    })
    this.persistLocalDraft(questions)
  },

  onSingleChange(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.updateQuestion(index, { draftAnswer: event.detail.value || '' })
  },

  onMultiChange(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.updateQuestion(index, { draftAnswer: event.detail.value || [] })
  },

  onJudgeChange(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.updateQuestion(index, { draftAnswer: event.detail.value || '' })
  },

  onTextInput(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.updateQuestion(index, { draftAnswer: event.detail.value || '' })
  },

  onReviewChange(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.updateQuestion(index, { markedForReview: Boolean(event.detail.value) })
  },

  persistLocalDraft(nextQuestions) {
    const questions = nextQuestions || this.data.questions || []
    if (!this.data.sessionId || !questions.length || !this.data.dirty) return
    saveAnswerDraft(this.data.sessionId, {
      examId: this.data.examId,
      updatedAt: Date.now(),
      questions: buildDraftSnapshot(questions),
    })
  },

  async saveAnswers(options = {}) {
    if (!this.data.questions.length || !this.data.sessionId) return
    this.setData({ saving: true })
    try {
      await api.saveAnswers(this.data.sessionId, buildSubmitAnswers(this.data.questions))
      clearAnswerDraft(this.data.sessionId)
      this.setData({
        dirty: false,
        saveHint: `${options.auto ? '已自动保存' : '已保存'} ${new Date().toLocaleTimeString()}`,
      })
      if (!options.silent) {
        wx.showToast({ title: '答案已保存', icon: 'success' })
      }
    } catch (error) {
      this.persistLocalDraft()
      wx.showToast({
        title: error && error.message ? error.message : '保存失败，已保留本地草稿',
        icon: 'none',
      })
      throw error
    } finally {
      this.setData({ saving: false })
    }
  },

  submitSession() {
    wx.showModal({
      title: '提交试卷',
      content: '提交后将结束当前会话，答案不能继续修改。确认提交吗？',
      success: async (result) => {
        if (!result.confirm) return
        await this.doSubmit()
      },
    })
  },

  async autoSubmitWhenTimeout() {
    if (this.data.submitting) return
    wx.showToast({
      title: '考试时间到，正在自动交卷',
      icon: 'none',
      duration: 1800,
    })
    await this.doSubmit({ autoTriggered: true })
  },

  async doSubmit(options = {}) {
    if (!this.data.sessionId) return
    this.setData({ submitting: true })
    try {
      if (this.data.questions.length) {
        await this.saveAnswers({ silent: true })
      }
      await api.submitSession(this.data.sessionId)
      clearAnswerDraft(this.data.sessionId)
      const app = getApp()
      if (app && typeof app.clearActiveSession === 'function') {
        app.clearActiveSession()
      }
      wx.redirectTo({
        url: `/pages/result/index?sessionId=${this.data.sessionId}`,
      })
    } catch (error) {
      if (!options.autoTriggered) {
        wx.showToast({
          title: error && error.message ? error.message : '提交失败',
          icon: 'none',
          duration: 2200,
        })
      }
    } finally {
      this.setData({ submitting: false })
    }
  },

  canReportAntiCheat() {
    return Boolean(this.data.sessionId) && !this.data.submitting
  },

  async reportAntiCheatEvent(eventType, metadata) {
    if (!this.canReportAntiCheat()) return
    try {
      await api.reportAntiCheatEvent(this.data.sessionId, {
        eventType,
        metadata: metadata || {},
      })
    } catch (error) {
      // 防作弊上报失败不阻断考试流程。
    }
  },

  reportSwitchScreen() {
    if (!this.canReportAntiCheat()) return
    this.reportAntiCheatEvent('SWITCH_SCREEN', {
      source: 'miniapp.onHide',
    })
  },
})
