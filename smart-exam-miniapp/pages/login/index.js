const config = require('../../config/index')
const api = require('../../services/api')

function normalizeRole(role) {
  return String(role || '').trim().toUpperCase()
}

Page({
  data: {
    apiBaseUrl: config.apiBaseUrl,
    username: '',
    password: '',
    loading: false,
  },

  onShow() {
    const app = getApp()
    if (app && app.isLoggedIn && app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/exams/index' })
    }
  },

  onUsernameInput(event) {
    this.setData({ username: event.detail.value || '' })
  },

  onPasswordInput(event) {
    this.setData({ password: event.detail.value || '' })
  },

  async submitLogin() {
    const username = String(this.data.username || '').trim()
    const password = String(this.data.password || '').trim()

    if (!username || !password) {
      wx.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }

    this.setData({ loading: true })

    try {
      const loginPayload = await api.login({ username, password })
      const app = getApp()
      if (app && typeof app.setSession === 'function') {
        app.setSession(loginPayload)
      }

      const me = await api.getMe()
      const mergedUser = {
        ...(loginPayload.user || {}),
        ...(me && me.profile ? me.profile : {}),
        id: me && me.id ? me.id : loginPayload.user && loginPayload.user.id,
        role: me && me.role ? me.role : loginPayload.user && loginPayload.user.role,
      }

      const finalSession = {
        ...loginPayload,
        user: mergedUser,
      }

      if (normalizeRole(finalSession.user && finalSession.user.role) !== 'STUDENT') {
        if (app && typeof app.clearSession === 'function') {
          app.clearSession()
        }
        throw new Error('当前小程序仅开放学生端使用')
      }

      if (app && typeof app.setSession === 'function') {
        app.setSession(finalSession)
      }

      wx.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/exams/index' })
      }, 200)
    } catch (error) {
      wx.showToast({
        title: error && error.message ? error.message : '登录失败',
        icon: 'none',
        duration: 2200,
      })
    } finally {
      this.setData({ loading: false })
    }
  },
})
