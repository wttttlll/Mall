import { describe, expect, it } from 'vitest'
import { validateProductForm, validateSkuForm } from './adminValidation'

describe('admin form validation', () => {
  it('requires core product fields', () => {
    expect(validateProductForm({ name: '', categoryId: 1, mainImage: 'x' })).toContain('商品名称')
    expect(validateProductForm({ name: '商品', categoryId: null, mainImage: 'x' })).toContain('分类')
    expect(validateProductForm({ name: '商品', categoryId: 1, mainImage: '' })).toContain('主图')
    expect(validateProductForm({ name: '商品', categoryId: 1, mainImage: 'https://x' })).toBe('')
  })

  it('validates SKU price and required spec values', () => {
    const specs = [{ id: 1, name: '颜色' }]
    expect(validateSkuForm({ skuCode: '', price: 1 }, specs)).toContain('SKU 编码')
    expect(validateSkuForm({ skuCode: 'SKU1', price: 0 }, specs)).toContain('销售价')
    expect(validateSkuForm({ skuCode: 'SKU1', price: 1.234 }, specs)).toContain('两位')
    expect(validateSkuForm({ skuCode: 'SKU1', price: 1 }, specs)).toContain('颜色')
    expect(
      validateSkuForm({ skuCode: 'SKU1', price: 1, selectedValueBySpec: { 1: 10 } }, specs)
    ).toBe('')
  })
})
