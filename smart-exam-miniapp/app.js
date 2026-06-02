const { clearSession, getSession, saveSession } = require('./utils/storage')

App({
  globalData: {
    session: null,
    activeSessionId: '',
  },

  onLaunch() {
    this.globalData.session = getSession()
  },

  setSession(session) {
    saveSession(session)
    this.globalData.session = session || null
  },

  clearSession() {
    clearSession()
    this.globalData.session = null
    this.globalData.activeSessionId = ''
  },

  getSession() {
    return this.globalData.session || getSession()
  },

  isLoggedIn() {
    const session = this.getSession()
    return Boolean(session && session.token)
  },

  setActiveSession(sessionId) {
    this.globalData.activeSessionId = String(sessionId || '').trim()
  },

  clearActiveSession() {
    this.globalData.activeSessionId = ''
  },
})
