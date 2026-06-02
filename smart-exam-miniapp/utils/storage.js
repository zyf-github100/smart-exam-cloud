const SESSION_KEY = 'smart_exam_miniapp_session'
const DRAFT_PREFIX = 'smart_exam_miniapp_answer_draft_'

function getSession() {
  try {
    return wx.getStorageSync(SESSION_KEY) || null
  } catch (error) {
    return null
  }
}

function saveSession(session) {
  if (!session) {
    clearSession()
    return
  }
  wx.setStorageSync(SESSION_KEY, session)
}

function clearSession() {
  try {
    wx.removeStorageSync(SESSION_KEY)
  } catch (error) {
    // 忽略本地会话清理失败。
  }
}

function draftKey(sessionId) {
  return `${DRAFT_PREFIX}${String(sessionId || '').trim()}`
}

function getAnswerDraft(sessionId) {
  if (!sessionId) return null
  try {
    return wx.getStorageSync(draftKey(sessionId)) || null
  } catch (error) {
    return null
  }
}

function saveAnswerDraft(sessionId, draft) {
  if (!sessionId || !draft) return
  try {
    wx.setStorageSync(draftKey(sessionId), draft)
  } catch (error) {
    // 本地草稿失败不阻断答题。
  }
}

function clearAnswerDraft(sessionId) {
  if (!sessionId) return
  try {
    wx.removeStorageSync(draftKey(sessionId))
  } catch (error) {
    // 忽略本地草稿清理失败。
  }
}

module.exports = {
  clearAnswerDraft,
  clearSession,
  getAnswerDraft,
  getSession,
  saveAnswerDraft,
  saveSession,
}
