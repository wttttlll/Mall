<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AfterSaleStatusBadge from '../components/AfterSaleStatusBadge.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import { afterSaleApi } from '../api/afterSale'
import { formatDateTime, formatMoney } from '../utils/format'

const afterSales = ref([])
const loading = ref(true)
const error = ref('')
const status = ref('')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))

async function loadAfterSales() {
  loading.value = true
  error.value = ''
  try {
    const data = await afterSaleApi.list({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      status: status.value || undefined
    })
    afterSales.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '售后加载失败'
  } finally {
    loading.value = false
  }
}

function setStatus(value) {
  if (status.value === value) return
  status.value = value
  page.pageNum = 1
  loadAfterSales()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadAfterSales()
}

onMounted(loadAfterSales)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">After Sales</span>
        <h1>我的售后</h1>
      </div>
      <RouterLink to="/orders" class="btn btn-ghost">查看订单</RouterLink>
    </div>

    <div class="tabs" role="tablist">
      <button type="button" class="tab" :class="{ active: status === '' }" @click="setStatus('')">全部</button>
      <button type="button" class="tab" :class="{ active: status === 'APPLIED' }" @click="setStatus('APPLIED')">待审核</button>
      <button type="button" class="tab" :class="{ active: status === 'APPROVED' }" @click="setStatus('APPROVED')">待退货</button>
      <button type="button" class="tab" :class="{ active: status === 'COMPLETED' }" @click="setStatus('COMPLETED')">已完成</button>
      <button type="button" class="tab" :class="{ active: status === 'REJECTED' }" @click="setStatus('REJECTED')">已拒绝</button>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadAfterSales" />
    <LoadingState v-if="loading" label="正在加载售后..." />

    <template v-else>
      <EmptyState v-if="!afterSales.length" title="暂无售后申请" description="已完成订单如有退货退款需求，可在订单详情中申请售后。">
        <RouterLink class="btn btn-primary" to="/orders">查看我的订单</RouterLink>
      </EmptyState>

      <div v-else class="after-sale-list">
        <article v-for="item in afterSales" :key="item.afterSaleNo" class="surface-card after-sale-card">
          <div class="after-sale-head">
            <div>
              <RouterLink :to="`/after-sales/${item.afterSaleNo}`" class="after-sale-no">
                {{ item.afterSaleNo }}
              </RouterLink>
              <span class="after-sale-time">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <AfterSaleStatusBadge :status="item.status" />
          </div>
          <div class="after-sale-body">
            <div>
              <span class="table-muted">原订单</span>
              <RouterLink :to="`/orders/${item.orderNo}`" class="order-link">{{ item.orderNo }}</RouterLink>
            </div>
            <div>
              <span class="table-muted">退款金额</span>
              <strong class="amount">{{ formatMoney(item.refundAmount) }}</strong>
            </div>
            <div class="reason-cell">
              <span class="table-muted">申请原因</span>
              <span>{{ item.reason }}</span>
            </div>
            <RouterLink class="btn btn-ghost btn-sm" :to="`/after-sales/${item.afterSaleNo}`">
              查看详情
            </RouterLink>
          </div>
        </article>
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
.after-sale-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.after-sale-card {
  padding: 18px;
}

.after-sale-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}

.after-sale-no {
  color: var(--text-strong);
  font-weight: 800;
  word-break: break-all;
}

.after-sale-time {
  margin-left: 10px;
  color: var(--text-soft);
  font-size: 12px;
}

.after-sale-body {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  padding-top: 13px;
  font-size: 13px;
}

.after-sale-body > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.after-sale-body .reason-cell {
  flex: 1;
  min-width: 140px;
}

.order-link {
  color: var(--primary);
  font-weight: 600;
  word-break: break-all;
}

.amount {
  color: var(--price);
}

.table-muted {
  color: var(--text-soft);
  font-size: 11px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 24px;
  color: var(--text-muted);
  font-size: 14px;
}

@media (max-width: 620px) {
  .after-sale-time {
    display: block;
    margin: 5px 0 0;
  }

  .after-sale-body {
    align-items: flex-start;
  }
}
</style>
