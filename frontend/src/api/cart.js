import http from './http'

export const cartApi = {
  get() {
    return http.get('/cart')
  },
  add(skuId, quantity, idempotencyKey) {
    return http.post(
      '/cart/items',
      { skuId, quantity },
      { headers: { 'Idempotency-Key': idempotencyKey } }
    )
  },
  updateQuantity(skuId, quantity) {
    return http.put(`/cart/items/${skuId}`, { quantity })
  },
  remove(skuId) {
    return http.delete(`/cart/items/${skuId}`)
  },
  setSelected(skuId, selected) {
    return http.put(`/cart/items/${skuId}/selected`, { selected })
  },
  selectAll(selected) {
    return http.put('/cart/selected', { selected })
  }
}
