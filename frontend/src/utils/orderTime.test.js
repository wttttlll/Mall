import { describe, expect, it } from 'vitest'
import { formatCountdown, remainingMs } from './orderTime'

describe('order time utilities', () => {
  it('calculates remaining milliseconds and never returns negative', () => {
    const now = new Date('2026-11-05T10:00:00').getTime()
    expect(remainingMs('2026-11-05T10:30:00', now)).toBe(30 * 60 * 1000)
    expect(remainingMs('2026-11-05T09:00:00', now)).toBe(0)
    expect(remainingMs(null, now)).toBeNull()
  })

  it('parses a deadline with an explicit timezone offset', () => {
    const now = new Date('2026-11-05T02:00:00Z').getTime()
    expect(remainingMs('2026-11-05T10:30:00+08:00', now)).toBe(30 * 60 * 1000)
  })

  it('formats countdown as mm:ss and h:mm:ss', () => {
    expect(formatCountdown(65_000)).toBe('01:05')
    expect(formatCountdown(3_665_000)).toBe('1:01:05')
    expect(formatCountdown(-1)).toBe('00:00')
  })
})
