import http from './http'

export const adminProductApi = {
  categories() {
    return http.get('/admin/product/categories')
  },
  createCategory(payload) {
    return http.post('/admin/product/categories', payload)
  },
  updateCategory(id, payload) {
    return http.put(`/admin/product/categories/${id}`, payload)
  },
  deleteCategory(id) {
    return http.delete(`/admin/product/categories/${id}`)
  },

  brands() {
    return http.get('/admin/product/brands')
  },

  spus(params = {}) {
    return http.get('/admin/product/spus', { params })
  },
  spu(id) {
    return http.get(`/admin/product/spus/${id}`)
  },
  createSpu(payload) {
    return http.post('/admin/product/spus', payload)
  },
  updateSpu(id, payload) {
    return http.put(`/admin/product/spus/${id}`, payload)
  },
  deleteSpu(id) {
    return http.delete(`/admin/product/spus/${id}`)
  },
  onShelf(id) {
    return http.put(`/admin/product/spus/${id}/on-shelf`)
  },
  offShelf(id) {
    return http.put(`/admin/product/spus/${id}/off-shelf`)
  },
  toDraft(id) {
    return http.put(`/admin/product/spus/${id}/draft`)
  },

  specs(spuId) {
    return http.get(`/admin/product/spus/${spuId}/specs`)
  },
  createSpec(spuId, payload) {
    return http.post(`/admin/product/spus/${spuId}/specs`, payload)
  },
  updateSpec(spuId, specId, payload) {
    return http.put(`/admin/product/spus/${spuId}/specs/${specId}`, payload)
  },
  deleteSpec(spuId, specId) {
    return http.delete(`/admin/product/spus/${spuId}/specs/${specId}`)
  },
  createSpecValue(spuId, specId, payload) {
    return http.post(`/admin/product/spus/${spuId}/specs/${specId}/values`, payload)
  },
  updateSpecValue(spuId, specId, valueId, payload) {
    return http.put(`/admin/product/spus/${spuId}/specs/${specId}/values/${valueId}`, payload)
  },
  deleteSpecValue(spuId, specId, valueId) {
    return http.delete(`/admin/product/spus/${spuId}/specs/${specId}/values/${valueId}`)
  },

  skus(spuId) {
    return http.get(`/admin/product/spus/${spuId}/skus`)
  },
  createSku(spuId, payload) {
    return http.post(`/admin/product/spus/${spuId}/skus`, payload)
  },
  updateSku(spuId, skuId, payload) {
    return http.put(`/admin/product/spus/${spuId}/skus/${skuId}`, payload)
  },
  deleteSku(spuId, skuId) {
    return http.delete(`/admin/product/spus/${spuId}/skus/${skuId}`)
  }
}
