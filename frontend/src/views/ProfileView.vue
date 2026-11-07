<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import { useAuthStore } from '../stores/auth'
import { formatDateTime } from '../utils/format'

const auth = useAuthStore()
const loading = ref(!auth.user)
const saving = ref(false)
const error = ref('')
const message = ref('')
const form = reactive({ nickname: '', phone: '', email: '' })

const user = computed(() => auth.user)
const initial = computed(() => (auth.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())
const quickLinks = [
  { to: '/orders', title: '我的订单', description: '查看订单、支付与物流状态' },
  { to: '/after-sales', title: '我的售后', description: '查看退货退款申请与处理进度' },
  { to: '/addresses', title: '收货地址', description: '管理常用收货信息' },
  { to: '/favorites', title: '我的收藏', description: '查看收藏的商品' },
  { to: '/reviews', title: '我的评价', description: '查看已提交的商品评价' }
]

function fillForm() {
  form.nickname = auth.user?.nickname || ''
  form.phone = auth.user?.phone || ''
  form.email = auth.user?.email || ''
}

async function ensureUser() {
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch (err) {
      error.value = err.message || '个人资料加载失败'
    }
  }
  fillForm()
  loading.value = false
}

async function save() {
  error.value = ''
  message.value = ''
  const nickname = form.nickname.trim()
  const phone = form.phone.trim()
  const email = form.email.trim()
  if (nickname.length > 64) {
    error.value = '昵称不能超过 64 个字符'
    return
  }
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
    error.value = '请填写正确的手机号'
    return
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    error.value = '请填写正确的邮箱'
    return
  }
  saving.value = true
  try {
    await auth.updateProfile({ nickname, phone, email })
    fillForm()
    message.value = '个人资料已更新'
  } catch (err) {
    error.value = err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(ensureUser)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">My Account</span>
        <h1>个人中心</h1>
      </div>
      <RouterLink to="/products" class="btn btn-ghost">继续购物</RouterLink>
    </div>

    <LoadingState v-if="loading" label="正在加载个人资料..." />
    <ErrorBanner v-else-if="error && !user" :message="error" retryable @retry="ensureUser" />

    <div v-else-if="user" class="profile-layout">
      <aside class="surface-card profile-card">
        <div class="profile-avatar">{{ initial }}</div>
        <h2>{{ auth.displayName || user.username }}</h2>
        <p class="profile-username">@{{ user.username }}</p>
        <p class="profile-since">加入于 {{ formatDateTime(user.createdAt) }}</p>
        <div class="profile-facts">
          <div>
            <span>用户名</span>
            <strong>{{ user.username }}</strong>
          </div>
          <div>
            <span>手机号</span>
            <strong>{{ user.phone || '未填写' }}</strong>
          </div>
          <div>
            <span>邮箱</span>
            <strong>{{ user.email || '未填写' }}</strong>
          </div>
        </div>
      </aside>

      <div class="profile-main">
        <section class="surface-card profile-section">
          <div class="section-title-row">
            <div>
              <h2>基础资料</h2>
              <p class="text-muted">用户名不可修改，其他资料会保存到当前账号。</p>
            </div>
          </div>

          <ErrorBanner :message="error" />
          <div v-if="message" class="success-banner">{{ message }}</div>

          <form class="form-stack" @submit.prevent="save">
            <div class="form-grid">
              <label class="field">
                <span>账号</span>
                <input :value="user.username" type="text" disabled />
              </label>
              <label class="field">
                <span>昵称</span>
                <input v-model="form.nickname" type="text" maxlength="64" placeholder="设置一个昵称" :disabled="saving" />
              </label>
              <label class="field">
                <span>手机号</span>
                <input v-model="form.phone" type="tel" maxlength="11" placeholder="选填" :disabled="saving" />
              </label>
              <label class="field">
                <span>邮箱</span>
                <input v-model="form.email" type="email" maxlength="128" placeholder="选填" :disabled="saving" />
              </label>
            </div>
            <div class="profile-form-actions">
              <button class="btn btn-primary" type="submit" :disabled="saving">
                {{ saving ? '保存中...' : '保存资料' }}
              </button>
            </div>
          </form>
        </section>

        <section class="quick-grid">
          <RouterLink v-for="link in quickLinks" :key="link.to" :to="link.to" class="surface-card quick-card">
            <div>
              <strong>{{ link.title }}</strong>
              <p>{{ link.description }}</p>
            </div>
            <span aria-hidden="true">→</span>
          </RouterLink>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.profile-card {
  padding: 26px 22px;
  text-align: center;
}

.profile-avatar {
  width: 78px;
  height: 78px;
  display: grid;
  place-items: center;
  margin: 0 auto 14px;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), #7c3aed);
  font-size: 28px;
  font-weight: 800;
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.24);
}

.profile-card h2 {
  margin: 0 0 5px;
  color: var(--text-strong);
  font-size: 20px;
}

.profile-username {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-size: 13px;
}

.profile-since {
  margin: 0 0 20px;
  color: var(--text-soft);
  font-size: 12px;
}

.profile-facts {
  padding-top: 16px;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
}

.profile-facts span {
  display: block;
  margin-bottom: 3px;
  color: var(--text-soft);
  font-size: 11px;
}

.profile-facts strong {
  color: var(--text);
  font-size: 13px;
  word-break: break-all;
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.profile-section {
  padding: 22px;
}

.profile-section h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 19px;
}

.profile-section .section-title-row {
  margin-bottom: 18px;
}

.profile-section .section-title-row p {
  margin: 5px 0 0;
  font-size: 12px;
}

.profile-form-actions {
  display: flex;
  justify-content: flex-end;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quick-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
  color: var(--text);
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.quick-card:hover {
  border-color: #c9d8f8;
  transform: translateY(-2px);
}

.quick-card strong {
  color: var(--text-strong);
  font-size: 15px;
}

.quick-card p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.quick-card > span {
  color: var(--primary);
  font-size: 18px;
}

@media (max-width: 820px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
