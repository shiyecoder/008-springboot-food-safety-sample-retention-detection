/** 全局格式化：时间、数字、百分比、状态标签 */
export function formatTime(v, withTime = true) {
  if (!v) return '—'
  const s = String(v).replace('T', ' ')
  return withTime ? s.slice(0, 16) : s.slice(0, 10)
}

export function formatDate(v) {
  return formatTime(v, false)
}

export function formatNum(v, digits = 1) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return digits === 0 ? String(Math.round(n)) : n.toFixed(digits)
}

export function formatInt(v) {
  return formatNum(v, 0)
}

export function formatPct(v) {
  return v === null || v === undefined ? '—' : `${Number(v).toFixed(1)}%`
}

/** 相对时间，用于时间轴与消息列表 */
export function fromNow(v) {
  if (!v) return '—'
  const t = new Date(String(v).replace(/-/g, '/')).getTime()
  const diff = Date.now() - t
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 3600 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400 * 1000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 7 * 86400 * 1000) return `${Math.floor(diff / 86400000)} 天前`
  return formatTime(v, false)
}

/** 留样状态 -> 标签样式 */
export const SAMPLE_TONE = {
  PENDING_REVIEW: 'warn',
  RETAINING: 'doing',
  PENDING_DISPOSE: 'bad',
  DISPOSED: 'plain',
  REJECTED: 'plain',
  PENDING: 'pending',
  OVERDUE: 'bad',
  TESTING: 'doing',
  QUALIFIED: 'ok',
  UNQUALIFIED: 'bad',
  RECHECK: 'warn',
  HANDLING: 'warn',
  CLOSED: 'ok'
}

export const toneOf = (status) => SAMPLE_TONE[status] || 'pending'

export const SEVERITY_TONE = { LOW: 'plain', NORMAL: 'warn', SERIOUS: 'bad', CRITICAL: 'bad' }

/** 数字千分位 */
export function thousands(v) {
  if (v === null || v === undefined) return '—'
  return String(v).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}
