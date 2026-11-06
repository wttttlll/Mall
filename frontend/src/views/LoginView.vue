<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ErrorBanner from '../components/ErrorBanner.vue'
import { useAuthStore } from '../stores/auth'
import { adminHomePath } from '../utils/permissions'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

function safeRedirect() {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  if (target.startsWith('/')) return target
  return auth.canAccessAdmin ? adminHomePath(auth.permissions) : '/products'
}

async function submit() {
  error.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    await router.replace(safeRedirect())
  } catch (err) {
    error.value = err.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <span class="brand-mark">M</span>
        <div>
          <h1>欢迎回来</h1>
          <p>登录 MallAgent，继续你的购物流程</p>
        </div>
      </div>

      <ErrorBanner :message="error" />

      <form class="form-stack" @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model="form.username" type="text" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>
        <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <p class="auth-switch">
        还没有账号？
        <RouterLink to="/register">立即注册</RouterLink>
      </p>
    </div>
  </div>
</template>
