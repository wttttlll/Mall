import http from './http'

export const authApi = {
  login(payload) {
    return http.post('/auth/login', payload)
  },
  register(payload) {
    return http.post('/auth/register', payload)
  },
  logout(refreshToken) {
    return http.post('/auth/logout', refreshToken ? { refreshToken } : {})
  },
  me() {
    return http.get('/users/me')
  }
}
