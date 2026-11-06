import http from './http'

export const orderApi = {
  create(addressId, idempotencyKey) {
    return http.post(
      '/orders',
      { addressId },
      { headers: { 'Idempotency-Key': idempotencyKey } }
    )
  },
  list(params = {}) {
    return http.get('/orders', { params })
  },
  detail(orderNo) {
    return http.get(`/orders/${orderNo}`)
  },
  cancel(orderNo) {
    return http.post(`/orders/${orderNo}/cancel`)
  },
  confirmReceipt(orderNo) {
    return http.post(`/orders/${orderNo}/confirm-receipt`)
  }
}
