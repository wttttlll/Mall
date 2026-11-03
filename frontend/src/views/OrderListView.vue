<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import OrderStatusBadge from '../components/OrderStatusBadge.vue'
import { orderApi } from '../api/order'
import { paymentApi } from '../api/payment'
import { formatDateTime, formatMoney } from '../utils/format'

const tabs = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'CANCELLED', label: '已取消' }
]

const orders = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const busyOrderNo = ref('')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const status = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const data = await orderApi.list({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      status: status.value || undefined
    })
    orders.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

function setStatus(value) {
  if (status.value === value) return
  status.value = value
  page.pageNum = 1
  loadOrders()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadOrders()
}

async function payOrder(order) {
  if (busyOrderNo.value) return
  busyOrderNo.value = order.orderNo
  error.value = ''
  message.value = ''
  try {
    await paymentApi.pay(order.orderNo)
    message.value = `订单 ${order.orderNo} 模拟支付成功`
    await loadOrders()
  } catch (err) {
    error.value = err.message || '支付失败'
  } finally {
    busyOrderNo.value = ''
  }
}

async function cancelOrder(order) {
  if (busyOrderNo.value) return
  if (!window.confirm(`确认取消订单 ${order.orderNo} 吗？`)) return
  busyOrderNo.value = order.orderNo
  error.value = ''
  message.value = ''
  try {
    await orderApi.cancel(order.orderNo)
    message.value = `订单 ${order.orderNo} 已取消`
    await loadOrders()
  } catch (err) {
    error.value = err.message || '取消失败'
  } finally {
    busyOrderNo.value = ''
  }
}

onMounted(loadOrders)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">My Orders</span>
        <h1>我的订单</h1>
      </div>
      <RouterLink to="/products" class="btn btn-ghost">继续购物</RouterLink>
    </div>

    <div class="tabs" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        class="tab"
        :class="{ active: status === tab.value }"
        @click="setStatus(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadOrders" />
    <div v-if="message" class="success-banner">{{ message }}</div>

    <LoadingState v-if="loading" label="正在加载订单..." />

    <template v-else>
      <EmptyState v-if="!orders.length" title="暂无订单" description="下单后可以在这里查看和支付。">
        <RouterLink class="btn btn-primary" to="/products">去购物</RouterLink>
      </EmptyState>

      <div v-else class="order-list">
        <article v-for="order in orders" :key="order.orderNo" class="order-card surface-card">
          <div class="order-card-head">
            <div>
              <RouterLink :to="`/orders/${order.orderNo}`" class="order-no">{{ order.orderNo }}</RouterLink>
              <span class="order-time">{{ formatDateTime(order.createdAt) }}</span>
            </div>
            <OrderStatusBadge :status="order.status" />
          </div>

          <div class="order-card-body">
            <div class="order-summary-line">
              <span class="text-muted">{{ order.itemCount }} 件商品</span>
              <strong class="order-total">{{ formatMoney(order.totalAmount) }}</strong>
            </div>
            <div class="order-actions">
              <RouterLink class="btn btn-ghost btn-sm" :to="`/orders/${order.orderNo}`">查看详情</RouterLink>
              <template v-if="order.status === 'PENDING_PAYMENT'">
                <button
                  class="btn btn-primary btn-sm"
                  type="button"
                  :disabled="busyOrderNo === order.orderNo"
                  @click="payOrder(order)"
                >
                  {{ busyOrderNo === order.orderNo ? '处理中...' : '模拟支付' }}
                </button>
                <button
                  class="btn btn-danger-ghost btn-sm"
                  type="button"
                  :disabled="busyOrderNo === order.orderNo"
                  @click="cancelOrder(order)"
                >
                  取消订单
                </button>
              </template>
            </div>
          </div>
        </article>
      </div>

      <div v-if="page.total > page.pageSize" class="pagination">
        <button class="btn btn-ghost btn-sm" :disabled="page.pageNum <= 1" @click="goPage(page.pageNum - 1)">
          上一页
        </button>
        <span>第 {{ page.pageNum }} / {{ totalPages }} 页</span>
        <button
          class="btn btn-ghost btn-sm"
          :disabled="page.pageNum >= totalPages"
          @click="goPage(page.pageNum + 1)"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-card {
  padding: 20px;
}

.order-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border);
}

.order-no {
  color: var(--text-strong);
  font-weight: 800;
}

.order-time {
  margin-left: 12px;
  color: var(--text-soft);
  font-size: 12px;
}

.order-card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 14px;
}

.order-summary-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.order-total {
  color: var(--price);
  font-size: 20px;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 620px) {
  .order-card-head,
  .order-card-body {
    flex-direction: column;
    align-items: flex-start;
  }

  .order-time {
    display: block;
    margin: 6px 0 0;
  }
}
</style>
