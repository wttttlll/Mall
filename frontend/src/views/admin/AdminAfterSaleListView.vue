<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AfterSaleStatusBadge from '../../components/AfterSaleStatusBadge.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import { adminAfterSaleApi } from '../../api/adminAfterSale'
import { formatDateTime, formatMoney } from '../../utils/format'

const afterSales = ref([])
const loading = ref(true)
const error = ref('')
const filters = reactive({ afterSaleNo: '', status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))

async function loadAfterSales() {
  loading.value = true
  error.value = ''
  try {
    const data = await adminAfterSaleApi.list({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      status: filters.status || undefined,
      afterSaleNo: filters.afterSaleNo.trim() || undefined
    })
    afterSales.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '售后加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
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
  <div>
    <div class="admin-page-heading">
      <div>
        <span class="eyebrow">After Sales</span>
        <h1>售后管理</h1>
      </div>
    </div>

    <form class="admin-toolbar after-sale-toolbar" @submit.prevent="search">
      <input v-model="filters.afterSaleNo" type="search" placeholder="搜索售后单号" />
      <select v-model="filters.status" @change="search">
        <option value="">全部状态</option>
        <option value="APPLIED">待审核</option>
        <option value="APPROVED">待退货</option>
        <option value="REJECTED">已拒绝</option>
        <option value="COMPLETED">已完成</option>
      </select>
      <button class="btn btn-primary" type="submit" :disabled="loading">搜索</button>
    </form>

    <ErrorBanner :message="error" retryable @retry="loadAfterSales" />
    <LoadingState v-if="loading" label="正在加载售后..." />

    <template v-else>
      <EmptyState v-if="!afterSales.length" title="暂无售后申请" description="调整筛选条件后再试。" />

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>售后单</th>
              <th>用户 / 原订单</th>
              <th>退款金额 / 原因</th>
              <th>状态</th>
              <th>申请 / 完成时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in afterSales" :key="item.afterSaleNo">
              <td>
                <RouterLink class="after-sale-link" :to="`/admin/after-sales/${item.afterSaleNo}`">
                  {{ item.afterSaleNo }}
                </RouterLink>
              </td>
              <td>
                <strong>{{ item.username || `用户 ${item.userId}` }}</strong>
                <div class="table-muted">{{ item.orderNo }}</div>
              </td>
              <td>
                <strong>{{ formatMoney(item.refundAmount) }}</strong>
                <div class="table-muted">{{ item.reason }}</div>
              </td>
              <td>
                <AfterSaleStatusBadge :status="item.status" />
              </td>
              <td class="table-muted">
                <div>申请：{{ formatDateTime(item.createdAt) }}</div>
                <div>完成：{{ formatDateTime(item.completedAt) }}</div>
              </td>
              <td>
                <RouterLink class="btn btn-ghost btn-sm" :to="`/admin/after-sales/${item.afterSaleNo}`">
                  查看详情
                </RouterLink>
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
.after-sale-toolbar {
  grid-template-columns: minmax(220px, 1fr) 170px auto;
}

.after-sale-link {
  color: var(--text-strong);
  font-weight: 700;
  word-break: break-all;
}
</style>
