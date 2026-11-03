<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <header class="app-header">
    <div class="container header-inner">
      <RouterLink to="/products" class="brand">
        <span class="brand-mark">M</span>
        <span class="brand-text">MallAgent</span>
      </RouterLink>

      <nav v-if="auth.isAuthenticated" class="main-nav">
        <RouterLink to="/products">商品</RouterLink>
        <RouterLink to="/cart">购物车</RouterLink>
        <RouterLink to="/orders">我的订单</RouterLink>
        <RouterLink to="/addresses">收货地址</RouterLink>
      </nav>

      <div class="header-actions">
        <template v-if="auth.isAuthenticated">
          <span class="user-name" :title="auth.user?.username">{{ auth.displayName }}</span>
          <button type="button" class="btn btn-ghost btn-sm" @click="logout">退出</button>
        </template>
        <template v-else>
          <RouterLink class="btn btn-ghost btn-sm" to="/login">登录</RouterLink>
          <RouterLink class="btn btn-primary btn-sm" to="/register">注册</RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(14px);
}

.header-inner {
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-strong);
  white-space: nowrap;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-strong));
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.25);
}

.brand-text {
  font-size: 19px;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.main-nav a {
  color: var(--text-muted);
  padding: 9px 13px;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 600;
  transition: background 0.15s ease, color 0.15s ease;
}

.main-nav a:hover {
  background: var(--surface-muted);
  color: var(--text-strong);
}

.main-nav a.router-link-active {
  color: var(--primary);
  background: var(--primary-soft);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.user-name {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--text-muted);
}

@media (max-width: 760px) {
  .header-inner {
    flex-wrap: wrap;
    gap: 10px;
    padding-top: 10px;
    padding-bottom: 10px;
  }

  .main-nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
  }

  .main-nav a {
    white-space: nowrap;
  }
}
</style>
