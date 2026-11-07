import http from './http'

export const adminOrderApi = {
  list(params = {}) {
    return http.get('/admin/orders', { params })
  },
  detail(orderNo) {
    return http.get(`/admin/orders/${orderNo}`)
  },
  ship(orderNo, payload) {
    return http.post(`/admin/orders/${orderNo}/ship`, payload)
  }
}
