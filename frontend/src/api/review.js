import http from './http'

export const reviewApi = {
  list(spuId, params = {}) {
    return http.get(`/products/${spuId}/reviews`, { params })
  },
  create(payload) {
    return http.post('/reviews', payload)
  },
  mine(params = {}) {
    return http.get('/reviews/mine', { params })
  }
}
