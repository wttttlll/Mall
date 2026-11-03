<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ErrorBanner from '../components/ErrorBanner.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  phone: '',
  email: ''
})
const loading = ref(false)
const error = ref('')

function validate() {
  if (!/^[a-zA-Z0-9_]{4,32}$/.test(form.username.trim())) {
    return '用户名需为 4-32 位字母、数字或下划线'
  }
  if (form.password.length < 8 || form.password.length > 64) {
    return '密码长度需在 8-64 位之间'
  }
  if (form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }
  if (form.phone.trim() && !/^1[3-9]\d{9}$/.test(form.phone.trim())) {
    return '手机号格式不正确'
  }
  return ''
}

async function submit() {
  error.value = validate()
  if (error.value) return

  loading.value = true
  try {
    const payload = {
      username: form.username.trim(),
      password: form.password
    }
    if (form.nickname.trim()) payload.nickname = form.nickname.trim()
    if (form.phone.trim()) payload.phone = form.phone.trim()
    if (form.email.trim()) payload.email = form.email.trim()

    await auth.register(payload)
    await router.replace('/products')
  } catch (err) {
    error.value = err.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card auth-card-wide">
      <div class="auth-brand">
        <span class="brand-mark">M</span>
        <div>
          <h1>创建账号</h1>
          <p>注册后即可浏览商品、下单和模拟支付</p>
        </div>
      </div>

      <ErrorBanner :message="error" />

      <form class="form-stack" @submit.prevent="submit">
        <label class="field">
          <span>用户名 *</span>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
            placeholder="4-32 位字母、数字或下划线"
          />
        </label>
        <div class="form-grid">
          <label class="field">
            <span>密码 *</span>
            <input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="至少 8 位"
            />
          </label>
          <label class="field">
            <span>确认密码 *</span>
            <input
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入密码"
            />
          </label>
        </div>
        <div class="form-grid">
          <label class="field">
            <span>昵称</span>
            <input v-model="form.nickname" type="text" placeholder="选填" />
          </label>
          <label class="field">
            <span>手机号</span>
            <input v-model="form.phone" type="tel" placeholder="选填" />
          </label>
        </div>
        <label class="field">
          <span>邮箱</span>
          <input v-model="form.email" type="email" placeholder="选填" />
        </label>
        <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
          {{ loading ? '注册中...' : '注册并登录' }}
        </button>
      </form>

      <p class="auth-switch">
        已有账号？
        <RouterLink to="/login">返回登录</RouterLink>
      </p>
    </div>
  </div>
</template>
