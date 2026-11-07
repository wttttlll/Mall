import http from './http'

export const favoriteApi = {
  list(params = {}) {
    return http.get('/favorites', { params })
  },
  status(spuIds = []) {
    const value = (Array.isArray(spuIds) ? spuIds : [spuIds])
      .filter((id) => Number.isFinite(Number(id)))
      .join(',')
    return http.get('/favorites/status', { params: { spuIds: value } })
  },
  add(spuId) {
    return http.post(`/favorites/${spuId}`)
  },
  remove(spuId) {
    return http.delete(`/favorites/${spuId}`)
  }
}
