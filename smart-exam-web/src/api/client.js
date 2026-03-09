import axios from 'axios'

const TOKEN_KEY = 'smart_exam_token'
const USER_KEY = 'smart_exam_user'
const BASE_KEY = 'smart_exam_api_base'
export const AUTH_CHANGED_EVENT = 'smart-exam-auth-changed'

const defaultBase = import.meta.env.VITE_API_BASE || '/api/v1'
const savedBase = localStorage.getItem(BASE_KEY)
const savedToken = localStorage.getItem(TOKEN_KEY)

let token = savedToken || ''

const notifyAuthChanged = () => {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(AUTH_CHANGED_EVENT))
  }
}

const http = axios.create({
  baseURL: savedBase || defaultBase,
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload === 'object' && Object.hasOwn(payload, 'code')) {
      if (payload.code !== 0) {
        return Promise.reject(new Error(payload.message || 'Request failed'))
      }
      return payload.data
    }
    return payload
  },
  (error) => {
    const serverMessage =
      error?.response?.data?.message ||
      error?.response?.data?.error ||
      error?.message ||
      'Network error'
    return Promise.reject(new Error(serverMessage))
  }
)

export const getApiBase = () => http.defaults.baseURL

export const setApiBase = (baseURL) => {
  const normalized = (baseURL || '').trim()
  http.defaults.baseURL = normalized || defaultBase
  localStorage.setItem(BASE_KEY, http.defaults.baseURL)
}

export const getToken = () => token

export const hasAuthSession = () => Boolean(token && getSavedUser())

export const getSessionUser = () => {
  if (!token) return null
  return getSavedUser()
}

export const setToken = (nextToken) => {
  token = (nextToken || '').trim()
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
  notifyAuthChanged()
}

export const getSavedUser = () => {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const setSavedUser = (user) => {
  if (user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(USER_KEY)
  }
  notifyAuthChanged()
}

export const clearAuth = () => {
  setToken('')
  setSavedUser(null)
}

export const api = {
  login: (payload) => http.post('/auth/login', payload),
  logout: () => http.post('/auth/logout'),
  getMe: () => http.get('/users/me'),
  listUsers: () => http.get('/users'),
  createQuestion: (payload) => http.post('/questions', payload),
  listQuestions: () => http.get('/questions'),
  getQuestion: (questionId) => http.get(`/questions/${questionId}`),
  createPaper: (payload) => http.post('/papers', payload),
  listPapers: (params) => http.get('/papers', { params }),
  getPaper: (paperId) => http.get(`/papers/${paperId}`),
  createExam: (payload) => http.post('/exams', payload),
  listPublishedExams: () => http.get('/exams/teachers/me'),
  listAssignedExams: () => http.get('/exams/students/me'),
  startExam: (examId) => http.post(`/exams/${examId}/start`),
  getSessionPaper: (sessionId) => http.get(`/sessions/${sessionId}/paper`),
  getSessionAnswers: (sessionId) => http.get(`/sessions/${sessionId}/answers`),
  saveAnswers: (sessionId, payload) => http.put(`/sessions/${sessionId}/answers`, payload),
  submitSession: (sessionId) => http.post(`/sessions/${sessionId}/submit`),
  getExamResultRelease: (examId) => http.get(`/grading/exams/${examId}/result-release`),
  updateExamResultRelease: (examId, released) => http.put(`/grading/exams/${examId}/result-release`, { released }),
  getStudentSessionResult: (sessionId) => http.get(`/grading/sessions/${sessionId}/result`),
  reportAntiCheatEvent: (sessionId, payload) => http.post(`/sessions/${sessionId}/anti-cheat/events`, payload),
  sessionAntiCheatRisk: (sessionId) => http.get(`/sessions/${sessionId}/anti-cheat/risk`),
  examAntiCheatRisks: (examId, params) => http.get(`/exams/${examId}/anti-cheat/risks`, { params }),
  listGradingTasks: (status) => http.get('/grading/tasks', { params: { status: status || undefined } }),
  manualScore: (taskId, payload) => http.post(`/grading/tasks/${taskId}/manual-score`, payload),
  scoreDistribution: (examId) => http.get(`/reports/exams/${examId}/score-distribution`),
  scoreSheet: (examId, params) => http.get(`/reports/exams/${examId}/score-sheet`, { params }),
  questionAccuracyTop: (examId, top) =>
    http.get(`/reports/exams/${examId}/question-accuracy-top`, { params: { top } }),
  adminOverview: () => http.get('/admin/overview'),
  adminUsers: (params) => http.get('/admin/users', { params }),
  adminUpdateUserStatus: (userId, payload) => http.put(`/admin/users/${userId}/status`, payload),
  adminUpdateUserRole: (userId, payload) => http.put(`/admin/users/${userId}/role`, payload),
  adminResetUserPassword: (userId, payload) => http.put(`/admin/users/${userId}/password/reset`, payload),
  adminRoles: () => http.get('/admin/roles'),
  adminPermissions: () => http.get('/admin/permissions'),
  adminUpdateRolePermissions: (roleCode, payload) => http.put(`/admin/roles/${roleCode}/permissions`, payload),
  adminConfigs: (params) => http.get('/admin/configs', { params }),
  adminUpsertConfig: (configKey, payload) => http.put(`/admin/configs/${configKey}`, payload),
  adminAudits: (params) => http.get('/admin/audits', { params }),
}
