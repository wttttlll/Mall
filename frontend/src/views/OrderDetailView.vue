<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import OrderStatusBadge from '../components/OrderStatusBadge.vue'
import { orderApi } from '../api/order'
import { paymentApi } from '../api/payment'
import { formatDateTime, formatMoney } from '../utils/format'

const route = useRoute()
const order = ref(null)
const loading = ref(true)
const error = ref('')
const message = ref('')
const acting = ref(false)

async function loadOrder() {
  loading.value = true
  error.value = ''
  try {
    order.value = await orderApi.detail(route.params.orderNo)
  } catch (err) {
    error.value = err.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

async function pay() {
  if (!order.value || acting.value) return
  acting.value = true
  error.value = ''
  message.value = ''
  try {
    await paymentApi.pay(order.value.orderNo)
    await loadOrder()
    message.value = '模拟支付成功，订单已更新为已支付'
  } catch (err) {
    error.value = err.message || '支付失败'
  } finally {
    acting.value = false
  }
}

async function cancel() {
  if (!order.value || acting.value) return
  if (!window.confirm('确认取消该订单吗？库存将立即释放。')) return
  acting.value = true
  error.value = ''
  message.value = ''
  try {
    await orderApi.cancel(order.value.orderNo)
    await loadOrder()
    message.value = '订单已取消，库存已释放'
  } catch (err) {
    error.value = err.message || '取消失败'
  } finally {
    acting.value = false
  }
}

onMounted(loadOrder)
</script>

<template>
  <div class="container page-section">
    <RouterLink to="/orders" class="back-link">← 返回我的订单</RouterLink>

    <LoadingState v-if="loading" label="正在加载订单详情..." />
    <ErrorBanner v-else-if="error && !order" :message="error" retryable @retry="loadOrder" />

    <template v-else-if="order">
      <div class="order-detail-layout">
        <div class="order-detail-main">
          <section class="surface-card detail-card">
            <div class="detail-head">
              <div>
                <span class="eyebrow">Order</span>
                <h1>{{ order.orderNo }}</h1>
                <p class="text-muted">下单时间：{{ formatDateTime(order.createdAt) }}</p>
              </div>
              <OrderStatusBadge :status="order.status" />
            </div>

            <ErrorBanner :message="error" />
            <div v-if="message" class="success-banner">{{ message }}</div>

            <div v-if="order.status === 'PENDING_PAYMENT'" class="order-actions">
              <button class="btn btn-primary" type="button" :disabled="acting" @click="pay">
                {{ acting ? '处理中...' : '模拟支付' }}
              </button>
              <button class="btn btn-danger-ghost" type="button" :disabled="acting" @click="cancel">
                取消订单
              </button>
            </div>
          </section>

          <section class="surface-card detail-card">
            <h2>收货信息</h2>
            <div class="receiver-card">
              <strong>{{ order.receiverName }}</strong>
              <span>{{ order.receiverPhone }}</span>
              <p>
                {{ order.receiverProvince }}{{ order.receiverCity }}{{ order.receiverDistrict
                }}{{ order.receiverDetailAddress }}
              </p>
              <p v-if="order.receiverPostalCode" class="text-muted">邮编：{{ order.receiverPostalCode }}</p>
            </div>
          </section>

          <section class="surface-card detail-card">
            <div class="section-title-row">
              <h2>商品明细</h2>
              <span class="text-muted">共 {{ order.itemCount }} 种</span>
            </div>
            <div v-for="item in order.items" :key="item.skuId" class="order-item">
              <div class="order-thumb">
                <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.spuName" />
                <div v-else class="placeholder">暂无图片</div>
              </div>
              <div class="order-item-main">
                <strong>{{ item.spuName || item.skuCode }}</strong>
                <span>{{ item.specText || item.skuCode }}</span>
              </div>
              <span>{{ formatMoney(item.unitPrice) }} × {{ item.quantity }}</span>
              <strong class="line-amount">{{ formatMoney(item.lineAmount) }}</strong>
            </div>
          </section>
        </div>

        <aside class="surface-card order-side">
          <h2>订单信息</h2>
          <div class="info-row">
            <span>订单状态</span>
            <OrderStatusBadge :status="order.status" />
          </div>
          <div class="info-row">
            <span>商品金额</span>
            <strong class="total">{{ formatMoney(order.totalAmount) }}</strong>
          </div>
          <div class="info-row">
            <span>支付时间</span>
            <span>{{ formatDateTime(order.paidAt) }}</span>
          </div>
          <div class="info-row">
            <span>取消时间</span>
            <span>{{ formatDateTime(order.cancelledAt) }}</span>
          </div>
          <p class="summary-note">
            当前为模拟支付，不涉及真实资金；MVP 暂未实现物流与售后。
          </p>
          <RouterLink class="btn btn-ghost btn-block" to="/orders">返回订单列表</RouterLink>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.order-detail-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-card {
  padding: 22px;
}

.detail-card h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}

.detail-head h1 {
  margin: 6px 0 6px;
  font-size: clamp(20px, 3vw, 28px);
  word-break: break-all;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.receiver-card {
  padding: 16px;
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.receiver-card strong {
  font-size: 16px;
  margin-right: 10px;
}

.receiver-card p {
  margin: 8px 0 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.order-item {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto auto;
  gap: 14px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-muted);
}

.order-thumb {
  width: 58px;
  height: 58px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-muted);
}

.order-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.order-thumb .placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 10px;
  color: var(--text-soft);
}

.order-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.order-item-main strong {
  color: var(--text-strong);
}

.order-item-main span {
  font-size: 12px;
}

.line-amount {
  color: var(--price);
}

.order-side {
  position: sticky;
  top: 88px;
  padding: 22px;
}

.order-side h2 {
  margin: 0 0 18px;
  font-size: 18px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--text-muted);
  font-size: 13px;
}

.info-row .total {
  color: var(--price);
  font-size: 22px;
}

@media (max-width: 900px) {
  .order-detail-layout {
    grid-template-columns: 1fr;
  }

  .order-side {
    position: static;
  }
}

@media (max-width: 600px) {
  .order-item {
    grid-template-columns: 52px minmax(0, 1fr) auto;
  }

  .order-item > span:nth-child(3) {
    display: none;
  }
}
</style>
