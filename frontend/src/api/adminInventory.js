import http from './http'

export const adminInventoryApi = {
  stocks(params = {}) {
    return http.get('/admin/inventory/stocks', { params })
  },
  stock(skuId) {
    return http.get(`/admin/inventory/stocks/${skuId}`)
  },
  adjust(payload, idempotencyKey) {
    return http.post('/admin/inventory/stocks/adjust', payload, {
      headers: { 'Idempotency-Key': idempotencyKey }
    })
  },
  flows(params = {}) {
    return http.get('/admin/inventory/flows', { params })
  }
}
