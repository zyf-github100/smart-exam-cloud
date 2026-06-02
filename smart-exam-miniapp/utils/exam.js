const { formatAnswer, formatScore, typeLabel } = require('./format')

function normalizeDraftValue(type, answerContent) {
  const normalizedType = String(type || '').trim().toUpperCase()
  if (normalizedType === 'MULTI') {
    return Array.isArray(answerContent) ? answerContent.map((item) => String(item)).filter(Boolean) : []
  }
  if (normalizedType === 'JUDGE') {
    if (answerContent === true || answerContent === 'true') return 'true'
    if (answerContent === false || answerContent === 'false') return 'false'
    return ''
  }
  if (answerContent === null || answerContent === undefined) return ''
  return String(answerContent)
}

function isAnswered(type, draftAnswer) {
  const normalizedType = String(type || '').trim().toUpperCase()
  if (normalizedType === 'MULTI') {
    return Array.isArray(draftAnswer) && draftAnswer.length > 0
  }
  if (normalizedType === 'JUDGE') {
    return draftAnswer === 'true' || draftAnswer === 'false'
  }
  return String(draftAnswer || '').trim().length > 0
}

function normalizeOptions(options) {
  if (!Array.isArray(options)) return []
  return options.map((option) => ({
    key: String(option.key || '').trim(),
    text: String(option.text || ''),
  }))
}

function mergePaperAndAnswers(paper, answers, localDraft) {
  const answerMap = {}
  ;(answers || []).forEach((item) => {
    answerMap[String(item.questionId || '')] = item || {}
  })

  const draftMap = {}
  if (localDraft && Array.isArray(localDraft.questions)) {
    localDraft.questions.forEach((item) => {
      draftMap[String(item.questionId || '')] = item || {}
    })
  }

  return (paper.questions || [])
    .slice()
    .sort((left, right) => (Number(left.orderNo) || 0) - (Number(right.orderNo) || 0))
    .map((question, index) => {
      const questionId = String(question.questionId || '')
      const savedAnswer = answerMap[questionId] || null
      const localAnswer = draftMap[questionId] || null
      const source = localAnswer || savedAnswer
      const draftAnswer = normalizeDraftValue(question.type, source && source.answerContent)
      return {
        ...question,
        questionId,
        orderNo: Number(question.orderNo) || index + 1,
        optionItems: normalizeOptions(question.options),
        type: String(question.type || '').trim().toUpperCase(),
        typeLabel: typeLabel(question.type),
        draftAnswer,
        markedForReview: Boolean(source && source.markedForReview),
        answered: isAnswered(question.type, draftAnswer),
      }
    })
}

function countAnswered(questions) {
  return (questions || []).filter((item) => item && item.answered).length
}

function toSubmitAnswerContent(type, draftAnswer) {
  const normalizedType = String(type || '').trim().toUpperCase()
  if (normalizedType === 'MULTI') {
    return Array.isArray(draftAnswer) ? draftAnswer.filter(Boolean).sort() : []
  }
  if (normalizedType === 'JUDGE') {
    if (draftAnswer === 'true') return true
    if (draftAnswer === 'false') return false
    return ''
  }
  return String(draftAnswer || '')
}

function buildSubmitAnswers(questions) {
  return (questions || []).map((question) => ({
    questionId: String(question.questionId || ''),
    answerContent: toSubmitAnswerContent(question.type, question.draftAnswer),
    markedForReview: Boolean(question.markedForReview),
  }))
}

function buildDraftSnapshot(questions) {
  return buildSubmitAnswers(questions)
}

function prepareResultQuestions(questions, detailReleased) {
  return (questions || []).map((question, index) => ({
    ...question,
    orderNo: Number(question.orderNo) || index + 1,
    typeLabel: typeLabel(question.type),
    myAnswerText: formatAnswer(question.myAnswer),
    standardAnswerText: detailReleased ? formatAnswer(question.standardAnswer) : '待开放',
    analysisText: detailReleased ? String(question.analysis || '暂无解析') : '待开放',
    scoreText: `${formatScore(question.gotScore)} / ${formatScore(question.maxScore)} 分`,
    correctnessClass: question.objective && question.correct === false ? 'status-danger' : 'status-running',
  }))
}

module.exports = {
  buildDraftSnapshot,
  buildSubmitAnswers,
  countAnswered,
  isAnswered,
  mergePaperAndAnswers,
  normalizeDraftValue,
  prepareResultQuestions,
}
