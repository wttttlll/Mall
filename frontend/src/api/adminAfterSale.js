import http from './http'

export const adminAfterSaleApi = {
  list(params = {}) {
    return http.get('/admin/after-sales', { params })
  },
  detail(afterSaleNo) {
    return http.get(`/admin/after-sales/${afterSaleNo}`)
  },
  approve(afterSaleNo) {
    return http.post(`/admin/after-sales/${afterSaleNo}/approve`)
  },
  reject(afterSaleNo, rejectReason) {
    return http.post(`/admin/after-sales/${afterSaleNo}/reject`, { rejectReason })
  },
  complete(afterSaleNo) {
    return http.post(`/admin/after-sales/${afterSaleNo}/complete`)
  }
}
