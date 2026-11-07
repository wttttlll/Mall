<script setup>
import { RouterView } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main">
      <RouterView />
    </main>
    <footer class="app-footer">
      <div class="container footer-main">
        <div class="footer-brand">
          <RouterLink to="/products" class="footer-logo">
            <span class="brand-mark">M</span>
            <strong>MallAgent</strong>
          </RouterLink>
          <p>把喜欢的好物带回家。<br />一个可完整体验下单、支付、履约与售后的电商演示项目。</p>
        </div>
        <div class="footer-column">
          <strong>逛商城</strong>
          <RouterLink to="/products">全部商品</RouterLink>
          <RouterLink to="/favorites">我的收藏</RouterLink>
          <RouterLink to="/orders">我的订单</RouterLink>
        </div>
        <div class="footer-column">
          <strong>我的账户</strong>
          <RouterLink to="/profile">个人中心</RouterLink>
          <RouterLink to="/addresses">收货地址</RouterLink>
          <RouterLink to="/after-sales">我的售后</RouterLink>
        </div>
        <div class="footer-column footer-about">
          <strong>关于 MallAgent</strong>
          <span>Vue 3 + Spring Boot 3</span>
          <span>模拟支付 · 模拟物流</span>
          <span>仅用于学习与项目演示</span>
        </div>
      </div>
      <div class="container footer-inner">
        <span>© 2026 MallAgent · MVP 电商演示</span>
        <span>Vue 3 + Vite · Spring Boot · MySQL</span>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-footer {
  margin-top: 56px;
  border-top: 1px solid var(--border);
  background:
    radial-gradient(circle at 8% 0%, rgba(219, 234, 254, 0.45), transparent 28%),
    rgba(255, 255, 255, 0.86);
}

.footer-main {
  display: grid;
  grid-template-columns: minmax(220px, 1.5fr) repeat(3, minmax(130px, 0.7fr));
  gap: 34px;
  padding-top: 36px;
  padding-bottom: 28px;
}

.footer-brand {
  max-width: 290px;
}

.footer-logo {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: var(--text-strong);
  font-size: 18px;
}

.footer-brand .brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: linear-gradient(135deg, var(--primary), var(--primary-strong));
  color: #fff;
  font-weight: 800;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.22);
}

.footer-brand p {
  margin: 13px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.8;
}

.footer-column {
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: 12px;
}

.footer-column strong {
  margin-bottom: 3px;
  color: var(--text-strong);
  font-size: 13px;
}

.footer-column a,
.footer-column span {
  width: fit-content;
  color: var(--text-muted);
}

.footer-column a:hover {
  color: var(--primary);
}

.footer-about span {
  color: var(--text-soft);
}

.footer-inner {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 18px;
  padding-bottom: 20px;
  border-top: 1px solid var(--border);
  color: var(--text-soft);
  font-size: 11px;
}

@media (max-width: 820px) {
  .footer-main {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 26px;
  }

  .footer-brand {
    grid-column: 1 / -1;
  }
}

@media (max-width: 520px) {
  .footer-main {
    grid-template-columns: 1fr 1fr;
  }

  .footer-inner {
    flex-direction: column;
  }
}
</style>
