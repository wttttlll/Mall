<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import OrderStatusBadge from '../../components/OrderStatusBadge.vue'
import { adminOrderApi } from '../../api/adminOrder'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime, formatMoney } from '../../utils/format'

const route = useRoute()
const auth = useAuthStore()
const order = ref(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const form = reactive({ shippingCompany: '', trackingNo: '' })

const canShip = computed(() => auth.hasPermission('order:info:ship'))
const canShipNow = computed(() => canShip.value && order.value?.status === 'PAID')

async function loadOrder() {
  loading.value = true
  error.value = ''
  try {
    order.value = await adminOrderApi.detail(route.params.orderNo)
  } catch (err) {
    error.value = err.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

async function ship() {
  if (!order.value || saving.value) return
  const shippingCompany = form.shippingCompany.trim()
  const trackingNo = form.trackingNo.trim()
  error.value = ''
  message.value = ''
  if (!shippingCompany) {
    error.value = '请填写快递公司'
    return
  }
  if (!trackingNo) {
    error.value = '请填写物流单号'
    return
  }
  if (shippingCompany.length > 64 || trackingNo.length > 64) {
    error.value = '快递公司和物流单号不能超过 64 个字符'
    return
  }
  if (!window.confirm(`确认订单 ${order.value.orderNo} 发货吗？`)) return

  saving.value = true
  try {
    order.value = await adminOrderApi.ship(order.value.orderNo, { shippingCompany, trackingNo })
    message.value = '发货成功，订单已进入待收货'
    form.shippingCompany = ''
    form.trackingNo = ''
  } catch (err) {
    error.value = err.message || '发货失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadOrder)
</script>

<template>
  <div>
    <RouterLink to="/admin/orders" class="back-link">← 返回订单管理</RouterLink>

    <LoadingState v-if="loading" label="正在加载订单详情..." />
    <ErrorBanner v-else-if="error && !order" :message="error" retryable @retry="loadOrder" />

    <template v-else-if="order">
      <div class="admin-page-heading order-detail-heading">
        <div>
          <span class="eyebrow">Order Detail</span>
          <h1>{{ order.orderNo }}</h1>
          <p class="table-muted">下单时间：{{ formatDateTime(order.createdAt) }}</p>
        </div>
        <div class="heading-status">
          <OrderStatusBadge :status="order.status" />
          <span
            class="status-badge"
            :class="order.paymentStatus === 'PAID' ? 'status-paid' : 'status-pending'"
          >
            {{ order.paymentStatus === 'PAID' ? '已支付' : '未支付' }}
          </span>
        </div>
      </div>

      <ErrorBanner :message="error" />
      <div v-if="message" class="success-banner">{{ message }}</div>

      <div class="order-detail-grid">
        <div class="detail-column">
          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>商品明细</h2>
              <span class="table-muted">共 {{ order.itemCount }} 种 / {{ formatMoney(order.totalAmount) }}</span>
            </div>
            <div v-for="item in order.items" :key="item.skuId" class="order-item">
              <div class="order-thumb">
                <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.spuName" />
                <div v-else class="placeholder">暂无图片</div>
              </div>
              <div class="order-item-main">
                <strong>{{ item.spuName || item.skuCode }}</strong>
                <span>{{ item.specText || item.skuCode }} · SKU {{ item.skuId }}</span>
              </div>
              <span class="table-muted">{{ formatMoney(item.unitPrice) }} × {{ item.quantity }}</span>
              <strong class="line-amount">{{ formatMoney(item.lineAmount) }}</strong>
            </div>
          </section>

          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>收货信息</h2>
            </div>
            <div class="receiver-grid">
              <div>
                <span class="table-muted">收货人</span>
                <strong>{{ order.receiverName }}</strong>
              </div>
              <div>
                <span class="table-muted">联系电话</span>
                <strong>{{ order.receiverPhone }}</strong>
              </div>
              <div class="receiver-address">
                <span class="table-muted">收货地址</span>
                <strong>
                  {{ order.receiverProvince }}{{ order.receiverCity }}{{ order.receiverDistrict
                  }}{{ order.receiverDetailAddress }}
                </strong>
              </div>
              <div v-if="order.receiverPostalCode">
                <span class="table-muted">邮编</span>
                <strong>{{ order.receiverPostalCode }}</strong>
              </div>
            </div>
          </section>

          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>物流信息</h2>
            </div>
            <div v-if="order.shippingCompany || order.trackingNo" class="logistics-grid">
              <div>
                <span class="table-muted">快递公司</span>
                <strong>{{ order.shippingCompany || '-' }}</strong>
              </div>
              <div>
                <span class="table-muted">物流单号</span>
                <strong>{{ order.trackingNo || '-' }}</strong>
              </div>
              <div>
                <span class="table-muted">发货时间</span>
                <strong>{{ formatDateTime(order.shippedAt) }}</strong>
              </div>
              <div v-if="order.completedAt">
                <span class="table-muted">完成时间</span>
                <strong>{{ formatDateTime(order.completedAt) }}</strong>
              </div>
            </div>
            <p v-else class="table-muted">尚未发货，暂未填写物流信息。</p>
          </section>
        </div>

        <aside class="detail-column">
          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>用户信息</h2>
            </div>
            <div class="info-line">
              <span>用户 ID</span>
              <strong>{{ order.userId }}</strong>
            </div>
            <div class="info-line">
              <span>用户名</span>
              <strong>{{ order.username || '-' }}</strong>
            </div>
            <div class="info-line">
              <span>昵称</span>
              <strong>{{ order.nickname || '-' }}</strong>
            </div>
          </section>

          <section v-if="canShipNow" class="admin-section-card">
            <div class="admin-section-title">
              <h2>订单发货</h2>
            </div>
            <form class="form-stack" @submit.prevent="ship">
              <label class="field">
                <span>快递公司 *</span>
                <input
                  v-model="form.shippingCompany"
                  type="text"
                  maxlength="64"
                  placeholder="例如：顺丰速运"
                  :disabled="saving"
                />
              </label>
              <label class="field">
                <span>物流单号 *</span>
                <input
                  v-model="form.trackingNo"
                  type="text"
                  maxlength="64"
                  placeholder="请输入物流单号"
                  :disabled="saving"
                />
              </label>
              <p class="admin-hint">仅保存快递公司和物流单号，不对接真实物流公司；发货不会再次修改库存。</p>
              <button class="btn btn-primary btn-block" type="submit" :disabled="saving">
                {{ saving ? '发货中...' : '确认发货' }}
              </button>
            </form>
          </section>
          <section v-else class="admin-section-card">
            <div class="admin-section-title">
              <h2>发货说明</h2>
            </div>
            <p class="table-muted" v-if="order.status === 'PENDING_PAYMENT'">订单尚未支付，不能发货。</p>
            <p class="table-muted" v-else-if="order.status === 'CANCELLED'">订单已取消，不能发货。</p>
            <p class="table-muted" v-else-if="!canShip">当前账号只有订单查看权限，不能执行发货。</p>
            <p class="table-muted" v-else-if="order.status === 'SHIPPED'">订单已发货，可在上方查看物流信息。</p>
            <p class="table-muted" v-else-if="order.status === 'COMPLETED'">订单已完成，不能再次发货。</p>
          </section>

          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>时间信息</h2>
            </div>
            <div class="info-line">
              <span>支付时间</span>
              <span>{{ formatDateTime(order.paidAt) }}</span>
            </div>
            <div class="info-line">
              <span>支付截止</span>
              <span>{{ formatDateTime(order.payDeadline) }}</span>
            </div>
            <div class="info-line">
              <span>发货时间</span>
              <span>{{ formatDateTime(order.shippedAt) }}</span>
            </div>
            <div class="info-line">
              <span>完成时间</span>
              <span>{{ formatDateTime(order.completedAt) }}</span>
            </div>
            <div class="info-line">
              <span>取消时间</span>
              <span>{{ formatDateTime(order.cancelledAt) }}</span>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-detail-heading {
  align-items: flex-start;
}

.order-detail-heading h1 {
  word-break: break-all;
}

.heading-status {
  display: flex;
  gap: 8px;
  align-items: center;
}

.order-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 18px;
  align-items: start;
}

.detail-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.admin-section-card h2 {
  margin: 0;
  font-size: 16px;
}

.order-item {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--border);
  font-size: 13px;
}

.order-item:first-of-type {
  border-top: 0;
}

.order-thumb {
  width: 52px;
  height: 52px;
  border-radius: 9px;
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
  color: var(--text-muted);
}

.line-amount {
  color: var(--price);
}

.receiver-grid,
.logistics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.receiver-grid > div,
.logistics-grid > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}

.receiver-address {
  grid-column: 1 / -1;
}

.info-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 11px;
  font-size: 13px;
  color: var(--text-muted);
}

.info-line:last-child {
  margin-bottom: 0;
}

.info-line strong {
  color: var(--text-strong);
  text-align: right;
  word-break: break-all;
}

@media (max-width: 980px) {
  .order-detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .order-item {
    grid-template-columns: 46px minmax(0, 1fr) auto;
  }

  .order-item > span:nth-child(3) {
    display: none;
  }

  .receiver-grid,
  .logistics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
