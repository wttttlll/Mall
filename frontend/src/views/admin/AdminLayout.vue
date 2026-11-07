<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { adminHomePath } from '../../utils/permissions'

const auth = useAuthStore()
const router = useRouter()

const homePath = computed(() => adminHomePath(auth.permissions))
const navItems = computed(() => [
  {
    to: '/admin/products',
    label: '商品管理',
    visible: auth.hasPermission('product:spu:read')
  },
  {
    to: '/admin/orders',
    label: '订单管理',
    visible: auth.hasPermission('order:info:read')
  },
  {
    to: '/admin/after-sales',
    label: '售后管理',
    visible: auth.hasPermission('aftersale:order:read')
  },
  {
    to: '/admin/categories',
    label: '分类管理',
    visible: auth.hasPermission('product:category:read')
  },
  {
    to: '/admin/inventory',
    label: '库存管理',
    visible: auth.hasPermission('inventory:stock:read')
  },
  {
    to: '/admin/inventory/flows',
    label: '库存流水',
    visible: auth.hasPermission('inventory:flow:read')
  }
].filter((item) => item.visible))

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink :to="homePath" class="admin-brand">
        <span class="brand-mark">M</span>
        <div>
          <strong>MallAgent</strong>
          <small>管理后台</small>
        </div>
      </RouterLink>

      <nav class="admin-nav">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="admin-sidebar-footer">
        <RouterLink to="/products" class="admin-store-link">← 返回商城</RouterLink>
        <div class="admin-user">
          <strong>{{ auth.displayName }}</strong>
          <span>{{ auth.roles.join(' / ') || '管理员' }}</span>
        </div>
        <button class="btn btn-ghost btn-sm btn-block" type="button" @click="logout">退出登录</button>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div>
          <span class="eyebrow">Admin Console</span>
          <strong>商城运营管理</strong>
        </div>
        <span
          class="admin-readonly"
          v-if="
            !auth.hasPermission('product:spu:create') &&
            !auth.hasPermission('inventory:stock:adjust') &&
            !auth.hasPermission('order:info:ship') &&
            !auth.hasPermission('aftersale:order:complete')
          "
        >
          只读模式
        </span>
      </header>
      <main class="admin-content">
        <RouterView :key="$route.fullPath" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  background: var(--bg);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 22px 16px;
  background: #0f172a;
  color: #cbd5e1;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
}

.admin-brand strong {
  display: block;
  font-size: 15px;
}

.admin-brand small {
  display: block;
  color: #94a3b8;
  font-size: 11px;
}

.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.admin-nav a {
  padding: 11px 12px;
  border-radius: 9px;
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 600;
}

.admin-nav a:hover {
  background: rgba(148, 163, 184, 0.12);
  color: #fff;
}

.admin-nav a.router-link-active {
  background: rgba(37, 99, 235, 0.28);
  color: #fff;
}

.admin-sidebar-footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-store-link {
  color: #93c5fd;
  font-size: 13px;
  font-weight: 700;
}

.admin-user strong {
  display: block;
  color: #fff;
  font-size: 14px;
}

.admin-user span {
  display: block;
  color: #94a3b8;
  font-size: 11px;
  margin-top: 2px;
}

.admin-sidebar-footer .btn {
  background: transparent;
  border-color: #334155;
  color: #cbd5e1;
}

.admin-main {
  min-width: 0;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 68px;
  padding: 14px 28px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(14px);
}

.admin-topbar strong {
  display: block;
  margin-top: 3px;
  color: var(--text-strong);
  font-size: 16px;
}

.admin-readonly {
  padding: 4px 10px;
  border-radius: 999px;
  background: #fef3c7;
  color: #92400e;
  font-size: 11px;
  font-weight: 800;
}

.admin-content {
  width: min(1280px, calc(100% - 48px));
  margin: 0 auto;
  padding: 26px 0 40px;
}

@media (max-width: 860px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    position: static;
    height: auto;
    padding: 14px;
  }

  .admin-nav {
    flex-direction: row;
    overflow-x: auto;
  }

  .admin-nav a {
    white-space: nowrap;
  }

  .admin-sidebar-footer {
    display: none;
  }

  .admin-topbar {
    padding: 12px 16px;
  }

  .admin-content {
    width: calc(100% - 24px);
    padding-top: 18px;
  }
}
</style>
