<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AfterSaleStatusBadge from '../../components/AfterSaleStatusBadge.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import { adminAfterSaleApi } from '../../api/adminAfterSale'
import { useAuthStore } from '../../stores/auth'
import {
  afterSaleProcessStatusText,
  formatDateTime,
  formatMoney,
  orderStatusText
} from '../../utils/format'

const route = useRoute()
const auth = useAuthStore()
const afterSale = ref(null)
const loading = ref(true)
const acting = ref(false)
const error = ref('')
const message = ref('')
const showReject = ref(false)
const rejectReason = ref('')

const canReview = computed(() => auth.hasPermission('aftersale:order:review'))
const canComplete = computed(() => auth.hasPermission('aftersale:order:complete'))
const canReviewNow = computed(() => canReview.value && afterSale.value?.status === 'APPLIED')
const canCompleteNow = computed(() => canComplete.value && afterSale.value?.status === 'APPROVED')

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
    afterSale.value = await adminAfterSaleApi.detail(route.params.afterSaleNo)
  } catch (err) {
    error.value = err.message || '售后详情加载失败'
  } finally {
    loading.value = false
  }
}

async function approve() {
  if (!afterSale.value || acting.value) return
  if (!window.confirm(`确认审核通过售后 ${afterSale.value.afterSaleNo} 吗？`)) return
  acting.value = true
  error.value = ''
  message.value = ''
  try {
    afterSale.value = await adminAfterSaleApi.approve(afterSale.value.afterSaleNo)
    message.value = '售后已审核通过，等待确认退货'
    showReject.value = false
  } catch (err) {
    error.value = err.message || '审核失败'
  } finally {
    acting.value = false
  }
}

function openReject() {
  showReject.value = true
  rejectReason.value = ''
  message.value = ''
  error.value = ''
}

async function reject() {
  if (!afterSale.value || acting.value) return
  if (!rejectReason.value.trim()) {
    error.value = '请填写拒绝原因'
    return
  }
  if (!window.confirm(`确认拒绝售后 ${afterSale.value.afterSaleNo} 吗？拒绝后不会退款或回补库存。`)) return
  acting.value = true
  error.value = ''
  message.value = ''
  try {
    afterSale.value = await adminAfterSaleApi.reject(
      afterSale.value.afterSaleNo,
      rejectReason.value.trim()
    )
    message.value = '售后已拒绝'
    showReject.value = false
  } catch (err) {
    error.value = err.message || '拒绝失败'
  } finally {
    acting.value = false
  }
}

async function complete() {
  if (!afterSale.value || acting.value) return
  if (!window.confirm('确认已收到退货并执行模拟退款吗？退款和库存回补将同时完成。')) return
  acting.value = true
  error.value = ''
  message.value = ''
  try {
    afterSale.value = await adminAfterSaleApi.complete(afterSale.value.afterSaleNo)
    message.value = '售后已完成：模拟退款成功，库存已回补'
  } catch (err) {
    error.value = err.message || '完成售后失败'
  } finally {
    acting.value = false
  }
}

onMounted(loadAfterSale)
</script>

<template>
  <div>
    <RouterLink to="/admin/after-sales" class="back-link">← 返回售后管理</RouterLink>

    <LoadingState v-if="loading" label="正在加载售后详情..." />
    <ErrorBanner v-else-if="error && !afterSale" :message="error" retryable @retry="loadAfterSale" />

    <template v-else-if="afterSale">
      <div class="admin-page-heading after-sale-detail-heading">
        <div>
          <span class="eyebrow">After Sale Detail</span>
          <h1>{{ afterSale.afterSaleNo }}</h1>
          <p class="table-muted">申请时间：{{ formatDateTime(afterSale.createdAt) }}</p>
        </div>
        <AfterSaleStatusBadge :status="afterSale.status" />
      </div>

      <ErrorBanner :message="error" />
      <div v-if="message" class="success-banner">{{ message }}</div>

      <div class="after-sale-admin-grid">
        <div class="detail-column">
          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>售后申请</h2>
              <strong class="amount">{{ formatMoney(afterSale.refundAmount) }}</strong>
            </div>
            <div class="info-grid">
              <div>
                <span class="table-muted">原订单</span>
                <RouterLink :to="`/admin/orders/${afterSale.orderNo}`">{{ afterSale.orderNo }}</RouterLink>
              </div>
              <div>
                <span class="table-muted">原订单状态</span>
                <strong>{{ orderStatusText(afterSale.orderStatus) }}</strong>
              </div>
              <div>
                <span class="table-muted">退货原因</span>
                <strong>{{ afterSale.reason }}</strong>
              </div>
              <div>
                <span class="table-muted">退款状态</span>
                <span class="status-badge" :class="processClass(afterSale.refundStatus)">
                  {{ afterSaleProcessStatusText(afterSale.refundStatus) }}
                </span>
              </div>
              <div>
                <span class="table-muted">库存回补状态</span>
                <span class="status-badge" :class="processClass(afterSale.restoreStatus)">
                  {{ afterSaleProcessStatusText(afterSale.restoreStatus) }}
                </span>
              </div>
              <div v-if="afterSale.refundNo">
                <span class="table-muted">退款单号</span>
                <strong>{{ afterSale.refundNo }}</strong>
              </div>
            </div>
            <div v-if="afterSale.description" class="description-block">
              <span>用户说明</span>
              <p>{{ afterSale.description }}</p>
            </div>
            <div v-if="afterSale.rejectReason" class="reject-block">
              <span>拒绝原因</span>
              <p>{{ afterSale.rejectReason }}</p>
            </div>
          </section>

          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>退货商品</h2>
            </div>
            <div v-for="item in afterSale.items" :key="item.skuId" class="after-sale-item">
              <div class="item-thumb">
                <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.spuName" />
                <div v-else class="placeholder">暂无图片</div>
              </div>
              <div class="item-main">
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
                <strong>{{ afterSale.receiverName }}</strong>
              </div>
              <div>
                <span class="table-muted">联系电话</span>
                <strong>{{ afterSale.receiverPhone }}</strong>
              </div>
              <div class="receiver-address">
                <span class="table-muted">退货地址</span>
                <strong>
                  {{ afterSale.receiverProvince }}{{ afterSale.receiverCity
                  }}{{ afterSale.receiverDistrict }}{{ afterSale.receiverDetailAddress }}
                </strong>
              </div>
            </div>
          </section>
        </div>

        <aside class="detail-column">
          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>用户信息</h2>
            </div>
            <div class="info-line">
              <span>用户 ID</span>
              <strong>{{ afterSale.userId }}</strong>
            </div>
            <div class="info-line">
              <span>用户名</span>
              <strong>{{ afterSale.username || '-' }}</strong>
            </div>
            <div class="info-line">
              <span>昵称</span>
              <strong>{{ afterSale.nickname || '-' }}</strong>
            </div>
          </section>

          <section v-if="canReviewNow || canCompleteNow" class="admin-section-card">
            <div class="admin-section-title">
              <h2>售后处理</h2>
            </div>
            <div v-if="canReviewNow" class="action-stack">
              <button class="btn btn-primary btn-block" type="button" :disabled="acting" @click="approve">
                {{ acting ? '处理中...' : '审核通过' }}
              </button>
              <button class="btn btn-danger-ghost btn-block" type="button" :disabled="acting" @click="openReject">
                审核拒绝
              </button>
            </div>
            <div v-if="canCompleteNow" class="action-stack">
              <button class="btn btn-primary btn-block" type="button" :disabled="acting" @click="complete">
                {{ acting ? '处理中...' : '确认收到退货并退款' }}
              </button>
            </div>
            <form v-if="showReject" class="form-stack reject-form" @submit.prevent="reject">
              <label class="field">
                <span>拒绝原因 *</span>
                <textarea v-model="rejectReason" rows="3" maxlength="255" placeholder="请填写拒绝原因" :disabled="acting"></textarea>
              </label>
              <div class="reject-actions">
                <button class="btn btn-danger-ghost" type="submit" :disabled="acting">确认拒绝</button>
                <button class="btn btn-ghost" type="button" :disabled="acting" @click="showReject = false">取消</button>
              </div>
            </form>
          </section>
          <section v-else class="admin-section-card">
            <div class="admin-section-title">
              <h2>售后处理</h2>
            </div>
            <p class="table-muted" v-if="afterSale.status === 'APPLIED'">当前账号没有审核权限。</p>
            <p class="table-muted" v-else-if="afterSale.status === 'APPROVED'">当前账号没有完成退款权限。</p>
            <p class="table-muted" v-else>该售后已处理完成。</p>
          </section>

          <section class="admin-section-card">
            <div class="admin-section-title">
              <h2>处理时间</h2>
            </div>
            <div class="info-line"><span>申请</span><span>{{ formatDateTime(afterSale.createdAt) }}</span></div>
            <div class="info-line"><span>审核</span><span>{{ formatDateTime(afterSale.reviewedAt) }}</span></div>
            <div class="info-line"><span>完成</span><span>{{ formatDateTime(afterSale.completedAt) }}</span></div>
            <div class="info-line"><span>退款</span><span>{{ formatDateTime(afterSale.refundedAt) }}</span></div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.after-sale-detail-heading {
  align-items: flex-start;
}

.after-sale-detail-heading h1 {
  word-break: break-all;
}

.after-sale-admin-grid {
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

.amount {
  color: var(--price);
  font-size: 18px;
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

.description-block,
.reject-block {
  margin-top: 16px;
  padding: 13px;
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

.after-sale-item {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--border);
  font-size: 13px;
}

.after-sale-item:first-of-type {
  border-top: 0;
}

.item-thumb {
  width: 52px;
  height: 52px;
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
  min-width: 0;
}

.item-main strong {
  color: var(--text-strong);
}

.item-main span {
  color: var(--text-muted);
  font-size: 12px;
}

.line-amount {
  color: var(--price);
}

.receiver-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.receiver-grid > div {
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
  color: var(--text-muted);
  font-size: 13px;
}

.info-line:last-child {
  margin-bottom: 0;
}

.info-line strong {
  color: var(--text-strong);
  text-align: right;
  word-break: break-all;
}

.action-stack {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.reject-form {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.reject-actions {
  display: flex;
  gap: 9px;
}

@media (max-width: 980px) {
  .after-sale-admin-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .info-grid,
  .receiver-grid {
    grid-template-columns: 1fr;
  }

  .after-sale-item {
    grid-template-columns: 46px minmax(0, 1fr) auto;
  }

  .after-sale-item > span:nth-child(3) {
    display: none;
  }
}
</style>
