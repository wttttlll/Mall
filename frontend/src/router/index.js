import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/products' },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('../views/ProductListView.vue')
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('../views/ProductDetailView.vue')
  },
  {
    path: '/cart',
    name: 'cart',
    component: () => import('../views/CartView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('../views/CheckoutView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('../views/OrderListView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/orders/:orderNo',
    name: 'order-detail',
    component: () => import('../views/OrderDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/addresses',
    name: 'addresses',
    component: () => import('../views/AddressView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/favorites',
    name: 'favorites',
    component: () => import('../views/FavoriteListView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/reviews',
    name: 'reviews',
    component: () => import('../views/MyReviewView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/after-sales',
    name: 'after-sales',
    component: () => import('../views/AfterSaleListView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/after-sales/apply',
    name: 'after-sale-apply',
    component: () => import('../views/AfterSaleApplyView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/after-sales/:afterSaleNo',
    name: 'after-sale-detail',
    component: () => import('../views/AfterSaleDetailView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/products' },
      {
        path: 'products',
        name: 'admin-products',
        component: () => import('../views/admin/AdminProductListView.vue'),
        meta: { permission: 'product:spu:read' }
      },
      {
        path: 'products/new',
        name: 'admin-product-new',
        component: () => import('../views/admin/AdminProductEditView.vue'),
        meta: { permission: 'product:spu:read' }
      },
      {
        path: 'products/:id',
        name: 'admin-product-edit',
        component: () => import('../views/admin/AdminProductEditView.vue'),
        meta: { permission: 'product:spu:read' }
      },
      {
        path: 'categories',
        name: 'admin-categories',
        component: () => import('../views/admin/AdminCategoryView.vue'),
        meta: { permission: 'product:category:read' }
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: () => import('../views/admin/AdminOrderListView.vue'),
        meta: { permission: 'order:info:read' }
      },
      {
        path: 'orders/:orderNo',
        name: 'admin-order-detail',
        component: () => import('../views/admin/AdminOrderDetailView.vue'),
        meta: { permission: 'order:info:read' }
      },
      {
        path: 'after-sales',
        name: 'admin-after-sales',
        component: () => import('../views/admin/AdminAfterSaleListView.vue'),
        meta: { permission: 'aftersale:order:read' }
      },
      {
        path: 'after-sales/:afterSaleNo',
        name: 'admin-after-sale-detail',
        component: () => import('../views/admin/AdminAfterSaleDetailView.vue'),
        meta: { permission: 'aftersale:order:read' }
      },
      {
        path: 'inventory',
        name: 'admin-inventory',
        component: () => import('../views/admin/AdminInventoryView.vue'),
        meta: { permission: 'inventory:stock:read' }
      },
      {
        path: 'inventory/flows',
        name: 'admin-inventory-flows',
        component: () => import('../views/admin/AdminInventoryFlowView.vue'),
        meta: { permission: 'inventory:flow:read' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.initialized) {
    await auth.initialize()
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.canAccessAdmin) {
    return { name: 'products' }
  }
  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) {
    return { name: 'products' }
  }
  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
    return { name: 'products' }
  }
  return true
})

export default router
