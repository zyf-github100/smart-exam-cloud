const api = require('../../services/api')
const { formatDateRange, examStatusClass, examStatusText, sessionStatusText } = require('../../utils/format')

function ensureLogin() {
  const app = getApp()
  if (!app || !app.isLoggedIn || !app.isLoggedIn()) {
    wx.reLaunch({ url: '/pages/login/index' })
    return false
  }
  return true
}

function prepareExamItem(item) {
  const status = String(item.status || '').toUpperCase()
  const sessionStatus = String(item.sessionStatus || '').toUpperCase()

  let actionText = '开始考试'
  let actionMode = 'start'

  if (sessionStatus === 'IN_PROGRESS') {
    actionText = '继续作答'
    actionMode = 'continue'
  } else if (sessionStatus === 'SUBMITTED' || sessionStatus === 'FORCE_SUBMITTED') {
    actionText = '查看成绩'
    actionMode = 'result'
  } else if (status === 'NOT_STARTED') {
    actionText = '未开始'
    actionMode = 'disabled'
  } else if (status === 'FINISHED') {
    actionText = '已结束'
    actionMode = 'disabled'
  }

  return {
    ...item,
    status: status || '',
    sessionStatus: sessionStatus || '',
    statusText: examStatusText(status),
    sessionStatusText: sessionStatusText(sessionStatus),
    statusClass: examStatusClass(status),
    timeRangeText: formatDateRange(item.startTime, item.endTime),
    actionText,
    actionMode,
  }
}

function confirmStartExam(exam) {
  return new Promise((resolve) => {
    wx.showModal({
      title: exam.actionMode === 'continue' ? '继续作答' : '进入考试',
      content: '请确认网络稳定、设备电量充足，并遵守考试规则。进入后离开页面会记录防作弊事件。',
      confirmText: exam.actionMode === 'continue' ? '继续' : '进入',
      cancelText: '取消',
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}

Page({
  data: {
    loading: false,
    actionLoadingExamId: '',
    examItems: [],
    totalCount: 0,
    runningCount: 0,
    submittedCount: 0,
    displayName: '未登录',
    usernameText: '-',
  },

  onShow() {
    if (!ensureLogin()) return
    this.loadPageData()
  },

  onPullDownRefresh() {
    this.loadPageData({ silent: true })
  },

  async loadPageData(options = {}) {
    if (!ensureLogin()) return

    const app = getApp()
    const session = app.getSession()
    const user = session && session.user ? session.user : {}

    this.setData({
      loading: true,
      displayName: user.realName || user.username || '未命名用户',
      usernameText: user.username || '-',
    })

    try {
      const [me, exams] = await Promise.all([api.getMe(), api.listAssignedExams()])
      const nextUser = {
        ...(session.user || {}),
        ...(me && me.profile ? me.profile : {}),
        id: me && me.id ? me.id : session.user && session.user.id,
        role: me && me.role ? me.role : session.user && session.user.role,
      }

      if (app && typeof app.setSession === 'function') {
        app.setSession({
          ...session,
          user: nextUser,
        })
      }

      const examItems = (exams || []).map(prepareExamItem)
      const runningCount = examItems.filter((item) => item.status === 'RUNNING').length
      const submittedCount = examItems.filter(
        (item) => item.sessionStatus === 'SUBMITTED' || item.sessionStatus === 'FORCE_SUBMITTED'
      ).length

      this.setData({
        examItems,
        totalCount: examItems.length,
        runningCount,
        submittedCount,
        displayName: nextUser.realName || nextUser.username || '未命名用户',
        usernameText: nextUser.username || '-',
      })
    } catch (error) {
      wx.showToast({
        title: error && error.message ? error.message : '加载考试失败',
        icon: 'none',
      })
    } finally {
      this.setData({ loading: false })
      if (options.silent) {
        wx.stopPullDownRefresh()
      }
    }
  },

  async openExam(event) {
    const examId = String(event.currentTarget.dataset.examId || '').trim()
    const sessionId = String(event.currentTarget.dataset.sessionId || '').trim()
    const actionMode = String(event.currentTarget.dataset.actionMode || '').trim()
    const exam = (this.data.examItems || []).find((item) => String(item.examId) === examId)

    if (!examId || !exam) return
    if (actionMode === 'disabled') return
    if (actionMode === 'result' && sessionId) {
      this.openResultBySessionId(sessionId)
      return
    }

    const approved = await confirmStartExam(exam)
    if (!approved) return

    this.setData({ actionLoadingExamId: examId })

    try {
      const payload = await api.startExam(examId)
      const targetSessionId = String(payload.sessionId || '').trim()
      if (!targetSessionId) {
        throw new Error('未获取到考试会话')
      }
      wx.navigateTo({
        url: `/pages/session/index?sessionId=${targetSessionId}&examId=${examId}&timeLimitSeconds=${payload.timeLimitSeconds || ''}`,
      })
    } catch (error) {
      wx.showToast({
        title: error && error.message ? error.message : '无法进入考试',
        icon: 'none',
        duration: 2200,
      })
    } finally {
      this.setData({ actionLoadingExamId: '' })
    }
  },

  openResult(event) {
    const sessionId = String(event.currentTarget.dataset.sessionId || '').trim()
    this.openResultBySessionId(sessionId)
  },

  openResultBySessionId(sessionId) {
    if (!sessionId) {
      wx.showToast({ title: '当前考试暂无成绩可查看', icon: 'none' })
      return
    }
    wx.navigateTo({
      url: `/pages/result/index?sessionId=${sessionId}`,
    })
  },

  goLogin() {
    const app = getApp()
    if (app && typeof app.clearSession === 'function') {
      app.clearSession()
    }
    wx.reLaunch({ url: '/pages/login/index' })
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确认清除当前会话并返回登录页吗？',
      success: (result) => {
        if (!result.confirm) return
        this.goLogin()
      },
    })
  },
})
