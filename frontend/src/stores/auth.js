import { defineStore } from 'pinia'
import { authApi } from '../api/auth'
import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from '../utils/authStorage'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: getAccessToken(),
    refreshToken: getRefreshToken(),
    user: null,
    initialized: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    displayName: (state) => state.user?.nickname || state.user?.username || ''
  },
  actions: {
    async initialize() {
      if (this.initialized) return
      if (this.accessToken) {
        try {
          await this.fetchMe()
        } catch {
          this.clear()
        }
      }
      this.initialized = true
    },
    async login(payload) {
      const tokens = await authApi.login(payload)
      this.setTokens(tokens)
      await this.fetchMe()
    },
    async register(payload) {
      const tokens = await authApi.register(payload)
      this.setTokens(tokens)
      await this.fetchMe()
    },
    async fetchMe() {
      this.user = await authApi.me()
      return this.user
    },
    async logout() {
      try {
        await authApi.logout(this.refreshToken)
      } catch {
        // 本地退出优先，不因服务端登出失败阻塞。
      } finally {
        this.clear()
      }
    },
    setTokens(tokens) {
      saveTokens(tokens)
      this.accessToken = tokens?.accessToken || ''
      this.refreshToken = tokens?.refreshToken || ''
    },
    clear() {
      clearTokens()
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
    }
  }
})
