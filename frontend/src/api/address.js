import http from './http'

export const addressApi = {
  list() {
    return http.get('/users/me/addresses')
  },
  create(payload) {
    return http.post('/users/me/addresses', payload)
  },
  update(id, payload) {
    return http.put(`/users/me/addresses/${id}`, payload)
  },
  remove(id) {
    return http.delete(`/users/me/addresses/${id}`)
  },
  setDefault(id) {
    return http.put(`/users/me/addresses/${id}/default`)
  }
}
