import http from './http'

export const productApi = {
  categories() {
    return http.get('/products/categories/tree')
  },
  list(params = {}) {
    return http.get('/products', { params })
  },
  detail(id) {
    return http.get(`/products/${id}`)
  }
}
