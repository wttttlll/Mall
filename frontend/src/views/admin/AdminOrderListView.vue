<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import OrderStatusBadge from '../../components/OrderStatusBadge.vue'
import { adminOrderApi } from '../../api/adminOrder'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime, formatMoney } from '../../utils/format'

const auth = useAuthStore()
const orders = ref([])
const loading = ref(true)
const error = ref('')
const filters = reactive({ orderNo: '', status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))
const canShip = computed(() => auth.hasPermission('order:info:ship'))

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const data = await adminOrderApi.list({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      status: filters.status || undefined,
      orderNo: filters.orderNo.trim() || undefined
    })
    orders.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.pageNum = 1
  loadOrders()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadOrders()
}

onMounted(loadOrders)
</script>

<template>
  <div>
    <div class="admin-page-heading">
      <div>
        <span class="eyebrow">Orders</span>
        <h1>订单管理</h1>
      </div>
    </div>

    <form class="admin-toolbar order-toolbar" @submit.prevent="search">
      <input v-model="filters.orderNo" type="search" placeholder="搜索订单号" />
      <select v-model="filters.status" @change="search">
        <option value="">全部状态</option>
        <option value="PENDING_PAYMENT">待支付</option>
        <option value="PAID">待发货</option>
        <option value="SHIPPED">待收货</option>
        <option value="COMPLETED">已完成</option>
        <option value="CANCELLED">已取消</option>
      </select>
      <button class="btn btn-primary" type="submit" :disabled="loading">搜索</button>
    </form>

    <ErrorBanner :message="error" retryable @retry="loadOrders" />

    <LoadingState v-if="loading" label="正在加载订单..." />

    <template v-else>
      <EmptyState v-if="!orders.length" title="暂无订单" description="调整筛选条件，或等待用户下单。" />

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>订单</th>
              <th>用户 / 收货人</th>
              <th>金额</th>
              <th>状态</th>
              <th>支付 / 发货</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.orderNo">
              <td>
                <RouterLink class="order-link" :to="`/admin/orders/${order.orderNo}`">
                  {{ order.orderNo }}
                </RouterLink>
                <div class="table-muted">{{ formatDateTime(order.createdAt) }}</div>
              </td>
              <td>
                <strong>{{ order.username || `用户 ${order.userId}` }}</strong>
                <div class="table-muted">{{ order.receiverName }} · {{ order.receiverPhone }}</div>
              </td>
              <td>
                <strong>{{ formatMoney(order.totalAmount) }}</strong>
                <div class="table-muted">{{ order.itemCount }} 件商品</div>
              </td>
              <td>
                <OrderStatusBadge :status="order.status" />
                <div class="table-muted">
                  <span class="status-badge" :class="order.paymentStatus === 'PAID' ? 'status-paid' : 'status-pending'">
                    {{ order.paymentStatus === 'PAID' ? '已支付' : '未支付' }}
                  </span>
                </div>
              </td>
              <td class="table-muted">
                <div>支付：{{ formatDateTime(order.paidAt) }}</div>
                <div>发货：{{ formatDateTime(order.shippedAt) }}</div>
              </td>
              <td>
                <div class="table-actions">
                  <RouterLink class="btn btn-ghost btn-sm" :to="`/admin/orders/${order.orderNo}`">
                    查看详情
                  </RouterLink>
                  <RouterLink
                    v-if="order.status === 'PAID' && canShip"
                    class="btn btn-primary btn-sm"
                    :to="`/admin/orders/${order.orderNo}`"
                  >
                    去发货
                  </RouterLink>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="page.total > page.pageSize" class="pagination">
        <button class="btn btn-ghost btn-sm" :disabled="page.pageNum <= 1" @click="goPage(page.pageNum - 1)">
          上一页
        </button>
        <span>第 {{ page.pageNum }} / {{ totalPages }} 页</span>
        <button class="btn btn-ghost btn-sm" :disabled="page.pageNum >= totalPages" @click="goPage(page.pageNum + 1)">
          下一页
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-toolbar {
  grid-template-columns: minmax(220px, 1fr) 170px auto;
}

.order-link {
  color: var(--text-strong);
  font-weight: 700;
  word-break: break-all;
}

.table-muted .status-badge {
  margin-top: 6px;
}
</style>
