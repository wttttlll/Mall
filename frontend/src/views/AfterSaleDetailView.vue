<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AfterSaleStatusBadge from '../components/AfterSaleStatusBadge.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import { afterSaleApi } from '../api/afterSale'
import {
  afterSaleProcessStatusText,
  formatDateTime,
  formatMoney,
  orderStatusText
} from '../utils/format'

const route = useRoute()
const afterSale = ref(null)
const loading = ref(true)
const error = ref('')

const canReapply = computed(() =>
  afterSale.value?.status === 'REJECTED' && afterSale.value?.orderStatus === 'COMPLETED'
)

function processClass(status) {
  return {
    SUCCESS: 'status-completed',
    PENDING: 'status-pending',
    NOT_APPLICABLE: 'status-cancelled',
    NOT_STARTED: 'status-default'
  }[status] || 'status-default'
}

async function loadAfterSale() {
  loading.value = true
  error.value = ''
  try {
    afterSale.value = await afterSaleApi.detail(route.params.afterSaleNo)
  } catch (err) {
    error.value = err.message || '售后详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadAfterSale)
</script>

<template>
  <div class="container page-section">
    <RouterLink to="/after-sales" class="back-link">← 返回我的售后</RouterLink>

    <LoadingState v-if="loading" label="正在加载售后详情..." />
    <ErrorBanner v-else-if="error && !afterSale" :message="error" retryable @retry="loadAfterSale" />

    <template v-else-if="afterSale">
      <div class="page-heading after-sale-heading">
        <div>
          <span class="eyebrow">After Sale</span>
          <h1>{{ afterSale.afterSaleNo }}</h1>
          <p class="text-muted">申请时间：{{ formatDateTime(afterSale.createdAt) }}</p>
        </div>
        <AfterSaleStatusBadge :status="afterSale.status" />
      </div>

      <ErrorBanner :message="error" />

      <div class="after-sale-layout">
        <div class="after-sale-main">
          <section class="surface-card detail-card">
            <h2>申请信息</h2>
            <div class="info-grid">
              <div>
                <span>原订单</span>
                <RouterLink :to="`/orders/${afterSale.orderNo}`">{{ afterSale.orderNo }}</RouterLink>
              </div>
              <div>
                <span>退款金额</span>
                <strong class="amount">{{ formatMoney(afterSale.refundAmount) }}</strong>
              </div>
              <div>
                <span>退货原因</span>
                <strong>{{ afterSale.reason }}</strong>
              </div>
              <div>
                <span>退款状态</span>
                <span class="status-badge" :class="processClass(afterSale.refundStatus)">
                  {{ afterSaleProcessStatusText(afterSale.refundStatus) }}
                </span>
              </div>
              <div>
                <span>库存回补</span>
                <span class="status-badge" :class="processClass(afterSale.restoreStatus)">
                  {{ afterSaleProcessStatusText(afterSale.restoreStatus) }}
                </span>
              </div>
              <div v-if="afterSale.refundNo">
                <span>退款单号</span>
                <strong>{{ afterSale.refundNo }}</strong>
              </div>
            </div>
            <div v-if="afterSale.description" class="description-block">
              <span>问题说明</span>
              <p>{{ afterSale.description }}</p>
            </div>
            <div v-if="afterSale.rejectReason" class="reject-block">
              <span>拒绝原因</span>
              <p>{{ afterSale.rejectReason }}</p>
            </div>
            <div v-if="canReapply" class="reapply-row">
              <RouterLink class="btn btn-primary" :to="`/after-sales/apply?orderNo=${afterSale.orderNo}`">
                重新申请售后
              </RouterLink>
            </div>
          </section>

          <section class="surface-card detail-card">
            <h2>退货商品</h2>
            <div v-for="item in afterSale.items" :key="item.skuId" class="after-sale-item">
              <div class="item-thumb">
                <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.spuName" />
                <div v-else class="placeholder">暂无图片</div>
              </div>
              <div class="item-main">
                <strong>{{ item.spuName || item.skuCode }}</strong>
                <span>{{ item.specText || item.skuCode }}</span>
              </div>
              <span>{{ formatMoney(item.unitPrice) }} × {{ item.quantity }}</span>
              <strong class="line-amount">{{ formatMoney(item.lineAmount) }}</strong>
            </div>
          </section>

          <section class="surface-card detail-card">
            <h2>收货信息</h2>
            <div class="receiver-card">
              <strong>{{ afterSale.receiverName }}</strong>
              <span>{{ afterSale.receiverPhone }}</span>
              <p>
                {{ afterSale.receiverProvince }}{{ afterSale.receiverCity
                }}{{ afterSale.receiverDistrict }}{{ afterSale.receiverDetailAddress }}
              </p>
            </div>
          </section>
        </div>

        <aside class="surface-card after-sale-side">
          <h2>处理进度</h2>
          <div class="timeline">
            <div class="timeline-item done">
              <span class="dot"></span>
              <div>
                <strong>提交申请</strong>
                <small>{{ formatDateTime(afterSale.createdAt) }}</small>
              </div>
            </div>
            <div class="timeline-item" :class="{ done: afterSale.reviewedAt }">
              <span class="dot"></span>
              <div>
                <strong>
                  {{
                    afterSale.reviewedAt
                      ? (afterSale.status === 'REJECTED' ? '审核拒绝' : '审核通过')
                      : '等待审核'
                  }}
                </strong>
                <small>{{ afterSale.reviewedAt ? formatDateTime(afterSale.reviewedAt) : '等待处理' }}</small>
              </div>
            </div>
            <div class="timeline-item" :class="{ done: afterSale.completedAt }">
              <span class="dot"></span>
              <div>
                <strong>退款与库存回补</strong>
                <small>{{ afterSale.completedAt ? formatDateTime(afterSale.completedAt) : '等待处理' }}</small>
              </div>
            </div>
          </div>

          <div class="info-row">
            <span>原订单状态</span>
            <span>{{ orderStatusText(afterSale.orderStatus) }}</span>
          </div>
          <div class="info-row">
            <span>订单金额</span>
            <strong>{{ formatMoney(afterSale.orderTotalAmount) }}</strong>
          </div>
          <div class="info-row">
            <span>支付时间</span>
            <span>{{ formatDateTime(afterSale.orderPaidAt) }}</span>
          </div>
          <div class="info-row">
            <span>完成时间</span>
            <span>{{ formatDateTime(afterSale.completedAt) }}</span>
          </div>
          <div class="info-row">
            <span>退款时间</span>
            <span>{{ formatDateTime(afterSale.refundedAt) }}</span>
          </div>
          <p class="summary-note">模拟退款不涉及真实资金；退款和库存回补由后端在同一事务中完成。</p>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.after-sale-heading {
  align-items: flex-start;
}

.after-sale-heading h1 {
  word-break: break-all;
}

.after-sale-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 18px;
  align-items: start;
}

.after-sale-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-card {
  padding: 22px;
}

.detail-card h2 {
  margin: 0 0 16px;
  font-size: 18px;
  color: var(--text-strong);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.info-grid > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 13px;
}

.info-grid > div > span:first-child {
  color: var(--text-soft);
  font-size: 11px;
}

.amount {
  color: var(--price);
  font-size: 18px;
}

.description-block,
.reject-block {
  margin-top: 18px;
  padding: 14px;
  border-radius: 10px;
  background: var(--surface-muted);
  font-size: 13px;
}

.reject-block {
  color: #9f1239;
  background: #fff1f2;
}

.description-block > span,
.reject-block > span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
}

.description-block p,
.reject-block p {
  margin: 6px 0 0;
  line-height: 1.7;
  white-space: pre-line;
}

.reapply-row {
  margin-top: 18px;
}

.after-sale-item {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-muted);
}

.after-sale-item:first-of-type {
  border-top: 0;
}

.item-thumb {
  width: 54px;
  height: 54px;
  border-radius: 9px;
  overflow: hidden;
  background: var(--surface-muted);
}

.item-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-thumb .placeholder {
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--text-soft);
  font-size: 10px;
}

.item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-main strong {
  color: var(--text-strong);
}

.item-main span {
  font-size: 12px;
}

.line-amount {
  color: var(--price);
}

.receiver-card {
  padding: 15px;
  border-radius: 10px;
  background: var(--surface-muted);
}

.receiver-card strong {
  margin-right: 9px;
}

.receiver-card p {
  margin: 7px 0 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.after-sale-side {
  position: sticky;
  top: 88px;
  padding: 22px;
}

.after-sale-side h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-bottom: 20px;
}

.timeline-item {
  display: flex;
  gap: 10px;
  color: var(--text-soft);
  font-size: 12px;
}

.timeline-item .dot {
  width: 9px;
  height: 9px;
  margin-top: 4px;
  border-radius: 50%;
  background: #d1d5db;
  flex: none;
}

.timeline-item.done .dot {
  background: var(--primary);
}

.timeline-item.done {
  color: var(--text);
}

.timeline-item strong {
  display: block;
  font-size: 13px;
}

.timeline-item small {
  display: block;
  margin-top: 3px;
  color: var(--text-soft);
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 13px;
}

.summary-note {
  margin: 16px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  color: var(--text-soft);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .after-sale-layout {
    grid-template-columns: 1fr;
  }

  .after-sale-side {
    position: static;
  }
}

@media (max-width: 560px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .after-sale-item {
    grid-template-columns: 48px minmax(0, 1fr) auto;
  }

  .after-sale-item > span:nth-child(3) {
    display: none;
  }
}
</style>
