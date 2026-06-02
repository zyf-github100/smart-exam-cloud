const { request } = require('./request')

module.exports = {
  login(payload) {
    return request({
      url: '/auth/login',
      method: 'POST',
      data: payload,
      auth: false,
    })
  },

  logout() {
    return request({
      url: '/auth/logout',
      method: 'POST',
    })
  },

  getMe() {
    return request({
      url: '/users/me',
    })
  },

  listAssignedExams() {
    return request({
      url: '/exams/students/me',
    })
  },

  startExam(examId) {
    return request({
      url: `/exams/${examId}/start`,
      method: 'POST',
    })
  },

  getSessionPaper(sessionId) {
    return request({
      url: `/sessions/${sessionId}/paper`,
    })
  },

  getSessionAnswers(sessionId) {
    return request({
      url: `/sessions/${sessionId}/answers`,
    })
  },

  saveAnswers(sessionId, answers) {
    return request({
      url: `/sessions/${sessionId}/answers`,
      method: 'PUT',
      data: { answers },
    })
  },

  submitSession(sessionId) {
    return request({
      url: `/sessions/${sessionId}/submit`,
      method: 'POST',
    })
  },

  getStudentSessionResult(sessionId) {
    return request({
      url: `/grading/sessions/${sessionId}/result`,
    })
  },

  reportAntiCheatEvent(sessionId, payload) {
    return request({
      url: `/sessions/${sessionId}/anti-cheat/events`,
      method: 'POST',
      data: payload,
    })
  },
}
