import http from './http'

export const afterSaleApi = {
  apply(payload) {
    return http.post('/after-sales', payload)
  },
  list(params = {}) {
    return http.get('/after-sales', { params })
  },
  detail(afterSaleNo) {
    return http.get(`/after-sales/${afterSaleNo}`)
  }
}
