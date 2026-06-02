const config = require('../config/index')
const { getSession } = require('../utils/storage')

function resolveSession() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (app && typeof app.getSession === 'function') {
    return app.getSession()
  }
  return getSession()
}

function redirectToLogin() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const current = pages.length ? pages[pages.length - 1] : null
  if (current && current.route === 'pages/login/index') return
  wx.reLaunch({ url: '/pages/login/index' })
}

function handleUnauthorized() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (app && typeof app.clearSession === 'function') {
    app.clearSession()
  }
  redirectToLogin()
}

function request(options) {
  const { url, method = 'GET', data, header = {}, auth = true } = options || {}

  return new Promise((resolve, reject) => {
    const session = resolveSession()
    const headers = { ...header }
    if (auth && session && session.token) {
      headers.Authorization = `Bearer ${session.token}`
    }

    wx.request({
      url: `${config.apiBaseUrl}${url}`,
      method,
      timeout: config.requestTimeout,
      data,
      header: headers,
      success(response) {
        const statusCode = Number(response.statusCode) || 0
        const payload = response.data

        if (statusCode === 401) {
          if (auth) handleUnauthorized()
          reject(new Error('登录已失效，请重新登录'))
          return
        }

        if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')) {
          if (Number(payload.code) !== 0) {
            if (auth && Number(payload.code) === 40100) {
              handleUnauthorized()
            }
            reject(new Error(payload.message || '请求失败'))
            return
          }
          resolve(payload.data)
          return
        }

        if (statusCode >= 200 && statusCode < 300) {
          resolve(payload)
          return
        }

        reject(new Error(`网络响应异常：${statusCode || '未知状态'}`))
      },
      fail(error) {
        reject(new Error(error && error.errMsg ? error.errMsg : '网络请求失败'))
      },
    })
  })
}

module.exports = {
  request,
}
