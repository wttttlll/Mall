<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AfterSaleStatusBadge from '../components/AfterSaleStatusBadge.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import OrderCountdown from '../components/OrderCountdown.vue'
import OrderStatusBadge from '../components/OrderStatusBadge.vue'
import StarRating from '../components/StarRating.vue'
import { afterSaleApi } from '../api/afterSale'
import { orderApi } from '../api/order'
import { paymentApi } from '../api/payment'
import { reviewApi } from '../api/review'
import { formatDateTime, formatMoney } from '../utils/format'

const route = useRoute()
const order = ref(null)
const loading = ref(true)
const error = ref('')
const reviewError = ref('')
const message = ref('')
const acting = ref(false)
const reviewsBySku = ref({})
const reviewsLoading = ref(false)
const afterSales = ref([])
const afterSaleLoading = ref(false)
const afterSaleError = ref('')
const reviewForm = reactive({
  visible: false,
  skuId: null,
  skuName: '',
  rating: 5,
  content: '',
  submitting: false
})
let expiredRefreshTimer = null
let silentRefreshing = false

const canReview = computed(() => order.value?.status === 'COMPLETED')
const latestAfterSale = computed(() => afterSales.value[0] || null)
const canApplyAfterSale = computed(() =>
  order.value?.status === 'COMPLETED' &&
  !afterSaleLoading.value &&
  !afterSaleError.value &&
  (!latestAfterSale.value || latestAfterSale.value.status === 'REJECTED')
)

async function loadOrder(silent = false) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    order.value = await orderApi.detail(route.params.orderNo)
    await Promise.all([loadMyReviews(), loadMyAfterSales()])
  } catch (err) {
    error.value = err.message || '订单加载失败'
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadMyAfterSales() {
  if (!order.value) return
  afterSaleLoading.value = true
  afterSaleError.value = ''
  try {
    const data = await afterSaleApi.list({
      pageNum: 1,
      pageSize: 10,
      orderNo: order.value.orderNo
    })
    afterSales.value = data.list || []
  } catch (err) {
    afterSaleError.value = err.message || '售后状态加载失败'
  } finally {
    afterSaleLoading.value = false
  }
}

async function loadMyReviews() {
  if (!order.value || order.value.status !== 'COMPLETED') {
    reviewsBySku.value = {}
    reviewForm.visible = false
    return
  }
  reviewsLoading.value = true
  reviewError.value = ''
  try {
    const data = await reviewApi.mine({
      pageNum: 1,
      pageSize: 100,
      orderNo: order.value.orderNo
    })
    reviewsBySku.value = Object.fromEntries((data.list || []).map((review) => [review.skuId, review]))
  } catch (err) {
    reviewError.value = err.message || '评价状态加载失败'
  } finally {
    reviewsLoading.value = false
  }
}

function openReview(item) {
  reviewForm.visible = true
  reviewForm.skuId = item.skuId
  reviewForm.skuName = item.spuName || item.skuCode || `SKU ${item.skuId}`
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewError.value = ''
  message.value = ''
}

function closeReview() {
  reviewForm.visible = false
  reviewForm.skuId = null
  reviewForm.content = ''
}

async function submitReview() {
  if (!reviewForm.skuId || reviewForm.submitting) return
  if (reviewForm.rating < 1 || reviewForm.rating > 5) {
    reviewError.value = '请选择 1～5 星评分'
    return
  }
  reviewForm.submitting = true
  reviewError.value = ''
  message.value = ''
  try {
    await reviewApi.create({
      orderNo: order.value.orderNo,
      skuId: reviewForm.skuId,
      rating: reviewForm.rating,
      content: reviewForm.content.trim() || null
    })
    message.value = '评价已提交，感谢你的分享'
    closeReview()
    await loadMyReviews()
  } catch (err) {
    reviewError.value = err.message || '评价提交失败'
  } finally {
    reviewForm.submitting = false
  }
}

function handleExpired() {
  if (expiredRefreshTimer) return
  expiredRefreshTimer = window.setTimeout(async () => {
    expiredRefreshTimer = null
    if (silentRefreshing) return
    silentRefreshing = true
    const previousStatus = order.value?.status
    try {
      await loadOrder(true)
      if (previousStatus === 'PENDING_PAYMENT' && order.value?.status === 'CANCELLED') {
        message.value = '订单已超时自动取消，库存已释放'
      }
    } finally {
      silentRefreshing = false
    }
  }, 1500)
}

async function pay() {
  if (!order.value || acting.value) return
  acting.value = true
  error.value = ''
  message.value = ''
  try {
    await paymentApi.pay(order.value.orderNo)
    await loadOrder()
    message.value = '模拟支付成功，订单已进入待发货'
  } catch (err) {
    if (err.code === 17005) {
      await loadOrder(true)
      error.value = '订单已超过支付时间，系统将自动取消'
    } else {
      error.value = err.message || '支付失败'
    }
  } finally {
    acting.value = false
  }
}

async function confirmReceipt() {
  if (!order.value || acting.value) return
  if (!window.confirm('确认已经收到该订单的商品吗？')) return
  acting.value = true
  error.value = ''
  message.value = ''
  try {
    await orderApi.confirmReceipt(order.value.orderNo)
    await loadOrder()
    message.value = '已确认收货，订单已完成'
  } catch (err) {
    error.value = err.message || '确认收货失败'
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
onBeforeUnmount(() => {
  if (expiredRefreshTimer) window.clearTimeout(expiredRefreshTimer)
})
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
                <OrderCountdown
                  v-if="order.status === 'PENDING_PAYMENT'"
                  :deadline="order.payDeadline"
                  active
                  @expired="handleExpired"
                />
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
            <div v-else-if="order.status === 'SHIPPED'" class="order-actions">
              <button class="btn btn-primary" type="button" :disabled="acting" @click="confirmReceipt">
                {{ acting ? '处理中...' : '确认收货' }}
              </button>
            </div>

            <div v-if="order.status === 'COMPLETED'" class="after-sale-entry">
              <div v-if="latestAfterSale" class="after-sale-status-line">
                <span class="text-muted">售后状态</span>
                <AfterSaleStatusBadge :status="latestAfterSale.status" />
                <RouterLink :to="`/after-sales/${latestAfterSale.afterSaleNo}`" class="after-sale-link">
                  查看售后
                </RouterLink>
              </div>
              <span v-else-if="afterSaleLoading" class="text-muted">正在加载售后状态...</span>
              <RouterLink
                v-if="canApplyAfterSale"
                class="btn btn-danger-ghost"
                :to="`/after-sales/apply?orderNo=${order.orderNo}`"
              >
                {{ latestAfterSale?.status === 'REJECTED' ? '重新申请售后' : '申请售后' }}
              </RouterLink>
            </div>
            <ErrorBanner :message="afterSaleError" />
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

          <section v-if="order.shippingCompany || order.trackingNo" class="surface-card detail-card">
            <h2>物流信息</h2>
            <div class="info-row">
              <span>快递公司</span>
              <strong>{{ order.shippingCompany || '-' }}</strong>
            </div>
            <div class="info-row">
              <span>物流单号</span>
              <span>{{ order.trackingNo || '-' }}</span>
            </div>
            <div class="info-row">
              <span>发货时间</span>
              <span>{{ formatDateTime(order.shippedAt) }}</span>
            </div>
          </section>

          <section class="surface-card detail-card">
            <div class="section-title-row">
              <h2>商品明细</h2>
              <span class="text-muted">共 {{ order.itemCount }} 种</span>
            </div>
            <div v-for="item in order.items" :key="item.skuId" class="order-item-wrap">
              <div class="order-item">
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
              <div v-if="canReview" class="order-item-review">
                <template v-if="reviewsBySku[item.skuId]">
                  <StarRating :model-value="reviewsBySku[item.skuId].rating" readonly size="sm" />
                  <span class="text-muted">已评价</span>
                </template>
                <button
                  v-else
                  class="btn btn-ghost btn-sm"
                  type="button"
                  :disabled="reviewsLoading"
                  @click="openReview(item)"
                >
                  评价商品
                </button>
              </div>
            </div>
          </section>

          <section v-if="reviewForm.visible" class="surface-card detail-card">
            <div class="section-title-row">
              <h2>评价商品</h2>
              <button class="btn btn-ghost btn-sm" type="button" @click="closeReview">取消</button>
            </div>
            <p class="text-muted review-target">{{ reviewForm.skuName }}</p>
            <ErrorBanner :message="reviewError" />
            <form class="form-stack" @submit.prevent="submitReview">
              <div class="rating-input">
                <span>评分</span>
                <StarRating v-model="reviewForm.rating" size="lg" />
                <strong>{{ reviewForm.rating }} 星</strong>
              </div>
              <label class="field">
                <span>文字评价（选填）</span>
                <textarea
                  v-model="reviewForm.content"
                  rows="4"
                  maxlength="1000"
                  placeholder="分享商品质量、使用体验等"
                  :disabled="reviewForm.submitting"
                ></textarea>
              </label>
              <div class="order-actions">
                <button class="btn btn-primary" type="submit" :disabled="reviewForm.submitting">
                  {{ reviewForm.submitting ? '提交中...' : '提交评价' }}
                </button>
                <button class="btn btn-ghost" type="button" :disabled="reviewForm.submitting" @click="closeReview">
                  取消
                </button>
              </div>
            </form>
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
          <div v-if="order.payDeadline" class="info-row">
            <span>支付截止</span>
            <span>{{ formatDateTime(order.payDeadline) }}</span>
          </div>
          <div class="info-row">
            <span>支付时间</span>
            <span>{{ formatDateTime(order.paidAt) }}</span>
          </div>
          <div v-if="order.shippedAt" class="info-row">
            <span>发货时间</span>
            <span>{{ formatDateTime(order.shippedAt) }}</span>
          </div>
          <div v-if="order.completedAt" class="info-row">
            <span>完成时间</span>
            <span>{{ formatDateTime(order.completedAt) }}</span>
          </div>
          <div class="info-row">
            <span>取消时间</span>
            <span>{{ formatDateTime(order.cancelledAt) }}</span>
          </div>
          <p class="summary-note">
            当前为模拟支付与模拟物流，不涉及真实资金与快递接口；物流信息以商家发货记录为准。
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

.detail-head :deep(.order-countdown) {
  margin-top: 8px;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.after-sale-entry {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.after-sale-status-line {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
}

.after-sale-link {
  color: var(--primary);
  font-weight: 700;
  font-size: 13px;
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

.order-item-wrap {
  padding: 12px 0;
  border-top: 1px solid var(--border);
}

.order-item {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto auto;
  gap: 14px;
  align-items: center;
  font-size: 13px;
  color: var(--text-muted);
}

.order-item-review {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
}

.review-target {
  margin: -4px 0 16px;
  font-size: 13px;
}

.rating-input {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 13px;
}

.rating-input strong {
  color: #b45309;
  font-size: 13px;
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
