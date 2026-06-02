const api = require('../../services/api')
const { prepareResultQuestions } = require('../../utils/exam')
const { formatScore } = require('../../utils/format')

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
    loading: true,
    ready: false,
    taskStatus: '-',
    waitingMessage: '成绩尚未生成',
    detailReleased: false,
    detailMessage: '',
    objectiveScore: '-',
    subjectiveScore: '-',
    totalScore: '-',
    questionItems: [],
  },

  onLoad(options) {
    if (!ensureLogin()) return
    const sessionId = String(options.sessionId || '').trim()
    if (!sessionId) {
      wx.showToast({ title: '缺少会话 ID', icon: 'none' })
      wx.navigateBack()
      return
    }
    this.setData({ sessionId })
  },

  onShow() {
    if (!ensureLogin()) return
    this.loadResult()
  },

  onPullDownRefresh() {
    this.loadResult({ silent: true })
  },

  async loadResult(options = {}) {
    if (!this.data.sessionId) return

    this.setData({ loading: true })
    try {
      const payload = await api.getStudentSessionResult(this.data.sessionId)
      const ready = Boolean(payload && payload.ready)
      const detailReleased = Boolean(payload && payload.detailReleased)
      const summary = payload && payload.summary ? payload.summary : {}
      const questionItems = ready ? prepareResultQuestions(payload.questions || [], detailReleased) : []

      this.setData({
        loading: false,
        ready,
        taskStatus: payload && payload.taskStatus ? payload.taskStatus : '-',
        waitingMessage: payload && payload.message ? payload.message : '成绩正在评阅中',
        detailReleased,
        detailMessage:
          payload && payload.detailMessage
            ? payload.detailMessage
            : '标准答案与解析将在考试结束后或老师发布后开放。',
        objectiveScore: formatScore(summary.objectiveScore),
        subjectiveScore: formatScore(summary.subjectiveScore),
        totalScore: formatScore(summary.totalScore),
        questionItems,
      })
    } catch (error) {
      this.setData({ loading: false })
      wx.showToast({
        title: error && error.message ? error.message : '加载成绩失败',
        icon: 'none',
      })
    } finally {
      if (options.silent) {
        wx.stopPullDownRefresh()
      }
    }
  },

  backToExams() {
    wx.reLaunch({ url: '/pages/exams/index' })
  },
})
