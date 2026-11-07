<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import OrderStatusBadge from '../components/OrderStatusBadge.vue'
import { afterSaleApi } from '../api/afterSale'
import { orderApi } from '../api/order'
import { formatDateTime, formatMoney } from '../utils/format'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const existing = ref(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const form = reactive({
  reason: '商品质量问题',
  description: ''
})

const reasons = ['商品质量问题', '商品与描述不符', '尺寸或型号不合适', '不想要了', '其他原因']
const hasActiveAfterSale = computed(() =>
  existing.value && ['APPLIED', 'APPROVED', 'COMPLETED'].includes(existing.value.status)
)
const canSubmit = computed(() =>
  order.value?.status === 'COMPLETED' && !hasActiveAfterSale.value && !submitting.value
)

async function loadPage() {
  loading.value = true
  error.value = ''
  const orderNo = typeof route.query.orderNo === 'string' ? route.query.orderNo : ''
  if (!orderNo) {
    error.value = '缺少订单号'
    loading.value = false
    return
  }
  try {
    order.value = await orderApi.detail(orderNo)
    const data = await afterSaleApi.list({ orderNo, pageNum: 1, pageSize: 10 })
    existing.value = (data.list || [])[0] || null
  } catch (err) {
    error.value = err.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canSubmit.value) return
  if (!form.reason.trim()) {
    error.value = '请选择退货原因'
    return
  }
  if (form.description.length > 1000) {
    error.value = '问题说明不能超过 1000 个字符'
    return
  }
  if (!window.confirm('确认提交整单退货退款申请吗？')) return
  submitting.value = true
  error.value = ''
  try {
    const created = await afterSaleApi.apply({
      orderNo: order.value.orderNo,
      reason: form.reason.trim(),
      description: form.description.trim() || null
    })
    await router.replace(`/after-sales/${created.afterSaleNo}`)
  } catch (err) {
    error.value = err.message || '售后申请失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadPage)
</script>

<template>
  <div class="container page-section">
    <RouterLink :to="order ? `/orders/${order.orderNo}` : '/after-sales'" class="back-link">
      ← 返回订单详情
    </RouterLink>

    <LoadingState v-if="loading" label="正在加载订单..." />
    <ErrorBanner v-else-if="error && !order" :message="error" retryable @retry="loadPage" />

    <template v-else-if="order">
      <div class="page-heading">
        <div>
          <span class="eyebrow">Return & Refund</span>
          <h1>申请售后</h1>
        </div>
        <OrderStatusBadge :status="order.status" />
      </div>

      <ErrorBanner :message="error" />

      <div v-if="order.status !== 'COMPLETED'" class="error-banner">
        仅已完成订单可以申请整单退货退款，当前订单状态不允许申请。
      </div>
      <div v-else-if="hasActiveAfterSale" class="error-banner">
        该订单已有有效售后申请，请前往售后详情查看处理进度。
        <RouterLink :to="`/after-sales/${existing.afterSaleNo}`">查看售后</RouterLink>
      </div>

      <div class="apply-layout">
        <section class="surface-card apply-card">
          <h2>退货退款申请</h2>
          <p class="text-muted">本售后为整单退货退款，退款金额以原支付记录为准。</p>
          <form class="form-stack" @submit.prevent="submit">
            <label class="field">
              <span>退货原因 *</span>
              <select v-model="form.reason" :disabled="!canSubmit">
                <option v-for="reason in reasons" :key="reason" :value="reason">{{ reason }}</option>
              </select>
            </label>
            <label class="field">
              <span>问题说明（选填，最多 1000 字）</span>
              <textarea
                v-model="form.description"
                rows="5"
                maxlength="1000"
                placeholder="请描述商品问题、退货原因等"
                :disabled="!canSubmit"
              ></textarea>
            </label>
            <div class="apply-actions">
              <button class="btn btn-primary" type="submit" :disabled="!canSubmit">
                {{ submitting ? '提交中...' : '提交售后申请' }}
              </button>
              <RouterLink class="btn btn-ghost" :to="`/orders/${order.orderNo}`">取消</RouterLink>
            </div>
          </form>
        </section>

        <aside class="surface-card apply-order">
          <h2>原订单</h2>
          <div class="info-row">
            <span>订单号</span>
            <strong class="order-no">{{ order.orderNo }}</strong>
          </div>
          <div class="info-row">
            <span>下单时间</span>
            <span>{{ formatDateTime(order.createdAt) }}</span>
          </div>
          <div class="info-row">
            <span>订单金额</span>
            <strong class="amount">{{ formatMoney(order.totalAmount) }}</strong>
          </div>
          <div class="order-items">
            <div v-for="item in order.items" :key="item.skuId" class="apply-item">
              <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.spuName" />
              <div>
                <strong>{{ item.spuName || item.skuCode }}</strong>
                <span>{{ item.specText || item.skuCode }} × {{ item.quantity }}</span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.apply-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 18px;
  align-items: start;
}

.apply-card,
.apply-order {
  padding: 22px;
}

.apply-card h2,
.apply-order h2 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--text-strong);
}

.apply-card > p {
  margin: 0 0 20px;
  font-size: 13px;
}

.apply-actions {
  display: flex;
  gap: 10px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 11px;
  color: var(--text-muted);
  font-size: 13px;
}

.order-no {
  color: var(--text-strong);
  word-break: break-all;
  text-align: right;
}

.amount {
  color: var(--price);
}

.order-items {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.apply-item {
  display: flex;
  align-items: center;
  gap: 9px;
}

.apply-item img {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--surface-muted);
}

.apply-item > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
}

.apply-item strong {
  color: var(--text-strong);
}

.apply-item span {
  color: var(--text-muted);
}

@media (max-width: 820px) {
  .apply-layout {
    grid-template-columns: 1fr;
  }
}
</style>
