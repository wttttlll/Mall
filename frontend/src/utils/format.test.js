import { describe, expect, it } from 'vitest'
import { formatMoney, formatPriceRange, orderStatusClass, orderStatusText } from './format'
import { createIdempotencyKey } from './idempotency'

describe('format utilities', () => {
  it('formats money with two decimals', () => {
    expect(formatMoney('88.8')).toContain('88.80')
    expect(formatMoney(0)).toContain('0.00')
  })

  it('formats a price range', () => {
    const range = formatPriceRange('10.00', '20.00')
    expect(range).toContain('10.00')
    expect(range).toContain('20.00')
  })

  it('maps order status text and class', () => {
    expect(orderStatusText('PENDING_PAYMENT')).toBe('待支付')
    expect(orderStatusText('PAID')).toBe('已支付')
    expect(orderStatusClass('CANCELLED')).toBe('status-cancelled')
  })
})

describe('idempotency key', () => {
  it('creates unique keys with a safe prefix and length', () => {
    const first = createIdempotencyKey('order')
    const second = createIdempotencyKey('order')
    expect(first).not.toBe(second)
    expect(first.startsWith('order-')).toBe(true)
    expect(first.length).toBeLessThanOrEqual(64)
    expect(first).toMatch(/^[A-Za-z0-9_-]+$/)
  })
})
