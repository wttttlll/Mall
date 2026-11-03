import axios from 'axios'
import { clearTokens, getAccessToken } from '../utils/authStorage'

export class ApiError extends Error {
  constructor(message, code, status) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload === 'object' && typeof payload.code === 'number') {
      if (payload.code === 0) {
        return payload.data
      }
      throw new ApiError(payload.message || '请求失败', payload.code, response.status)
    }
    return payload
  },
  (error) => {
    const status = error.response?.status
    const payload = error.response?.data
    const url = error.config?.url || ''
    const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register')

    if (status === 401 && !isAuthEndpoint) {
      clearTokens()
      const current = `${window.location.pathname}${window.location.search}`
      if (!window.location.pathname.startsWith('/login') && !window.location.pathname.startsWith('/register')) {
        window.location.assign(`/login?redirect=${encodeURIComponent(current)}`)
      }
    }

    const message = payload?.message || error.message || '网络异常，请稍后重试'
    return Promise.reject(new ApiError(message, payload?.code, status))
  }
)

export default http
