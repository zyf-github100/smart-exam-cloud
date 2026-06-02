const TYPE_LABEL_MAP = {
  SINGLE: '单选题',
  MULTI: '多选题',
  JUDGE: '判断题',
  FILL: '填空题',
  SHORT: '简答题',
}

function pad2(value) {
  return String(value).padStart(2, '0')
}

function toDate(value) {
  if (!value) return null
  const normalized = String(value).replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function formatDateTime(value) {
  if (!value) return '-'
  const date = toDate(value)
  if (!date) return String(value)
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(
    date.getHours()
  )}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

function formatDateRange(startTime, endTime) {
  return `${formatDateTime(startTime)} ~ ${formatDateTime(endTime)}`
}

function formatScore(value) {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  if (Number.isInteger(num)) return String(num)
  return String(num).replace(/(\.\d*?[1-9])0+$/, '$1').replace(/\.0+$/, '')
}

function formatAnswer(value) {
  if (value === null || value === undefined || value === '') return '未作答'
  if (Array.isArray(value)) return value.length ? value.join(', ') : '未作答'
  if (value === true) return '正确'
  if (value === false) return '错误'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function typeLabel(type) {
  const normalized = String(type || '').trim().toUpperCase()
  return TYPE_LABEL_MAP[normalized] || normalized || '-'
}

function examStatusText(status) {
  const normalized = String(status || '').trim().toUpperCase()
  if (normalized === 'NOT_STARTED') return '未开始'
  if (normalized === 'RUNNING') return '进行中'
  if (normalized === 'FINISHED') return '已结束'
  return normalized || '-'
}

function sessionStatusText(status) {
  const normalized = String(status || '').trim().toUpperCase()
  if (normalized === 'IN_PROGRESS') return '答题中'
  if (normalized === 'SUBMITTED') return '已提交'
  if (normalized === 'FORCE_SUBMITTED') return '已自动交卷'
  return normalized || '未进入'
}

function examStatusClass(status) {
  const normalized = String(status || '').trim().toUpperCase()
  if (normalized === 'RUNNING') return 'status-running'
  if (normalized === 'NOT_STARTED') return 'status-pending'
  if (normalized === 'FINISHED') return 'status-finished'
  return 'status-pending'
}

function sessionStatusClass(status) {
  const normalized = String(status || '').trim().toUpperCase()
  if (normalized === 'IN_PROGRESS') return 'status-running'
  if (normalized === 'SUBMITTED') return 'status-finished'
  if (normalized === 'FORCE_SUBMITTED') return 'status-danger'
  return 'status-pending'
}

function formatCountdown(seconds) {
  const safeSeconds = Math.max(0, Number(seconds) || 0)
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.floor((safeSeconds % 3600) / 60)
  const remainingSeconds = safeSeconds % 60
  return `${pad2(hours)}:${pad2(minutes)}:${pad2(remainingSeconds)}`
}

module.exports = {
  examStatusClass,
  examStatusText,
  formatAnswer,
  formatCountdown,
  formatDateRange,
  formatDateTime,
  formatScore,
  sessionStatusClass,
  sessionStatusText,
  typeLabel,
}
