export function remainingMs(deadline, now = Date.now()) {
  if (!deadline) return null
  const timestamp = new Date(deadline).getTime()
  if (Number.isNaN(timestamp)) return null
  return Math.max(0, timestamp - now)
}

export function formatCountdown(ms) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
}
