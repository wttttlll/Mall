import { describe, expect, it } from 'vitest'
import { canAccessAdmin, hasPermission } from './permissions'

describe('permission helpers', () => {
  it('allows product, inventory or order readers into admin', () => {
    expect(canAccessAdmin(['product:spu:read'])).toBe(true)
    expect(canAccessAdmin(['inventory:flow:read'])).toBe(true)
    expect(canAccessAdmin(['order:info:read'])).toBe(true)
    expect(canAccessAdmin(['aftersale:order:read'])).toBe(true)
    expect(canAccessAdmin(['cart:read'])).toBe(false)
    expect(canAccessAdmin([])).toBe(false)
  })

  it('checks exact permission codes', () => {
    const permissions = ['product:spu:read', 'inventory:stock:read']
    expect(hasPermission(permissions, 'product:spu:read')).toBe(true)
    expect(hasPermission(permissions, 'inventory:stock:adjust')).toBe(false)
  })
})
