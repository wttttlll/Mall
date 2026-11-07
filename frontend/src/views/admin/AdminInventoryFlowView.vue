<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import EmptyState from '../../components/EmptyState.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import { adminInventoryApi } from '../../api/adminInventory'
import { formatDateTime } from '../../utils/format'

const route = useRoute()
const flows = ref([])
const loading = ref(true)
const error = ref('')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const filters = reactive({
  skuId: typeof route.query.skuId === 'string' ? route.query.skuId : '',
  bizType: '',
  bizNo: '',
  refBizNo: ''
})

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))

async function loadFlows() {
  loading.value = true
  error.value = ''
  try {
    const data = await adminInventoryApi.flows({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      skuId: filters.skuId || undefined,
      bizType: filters.bizType || undefined,
      bizNo: filters.bizNo.trim() || undefined,
      refBizNo: filters.refBizNo.trim() || undefined
    })
    flows.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '库存流水加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.pageNum = 1
  loadFlows()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadFlows()
}

onMounted(loadFlows)

watch(
  () => route.query.skuId,
  (value) => {
    filters.skuId = typeof value === 'string' ? value : ''
    search()
  }
)
</script>

<template>
  <div>
    <div class="admin-page-heading">
      <div>
        <span class="eyebrow">Inventory Flows</span>
        <h1>库存流水</h1>
      </div>
      <RouterLink to="/admin/inventory" class="btn btn-ghost">返回库存</RouterLink>
    </div>

    <form class="admin-toolbar flows-toolbar" @submit.prevent="search">
      <input v-model="filters.skuId" type="number" min="1" placeholder="SKU ID" />
      <select v-model="filters.bizType" @change="search">
        <option value="">全部类型</option>
        <option value="ORDER_LOCK">ORDER_LOCK</option>
        <option value="ORDER_DEDUCT">ORDER_DEDUCT</option>
        <option value="ORDER_RELEASE">ORDER_RELEASE</option>
        <option value="RETURN_RESTORE">RETURN_RESTORE</option>
        <option value="ADMIN_ADJUST">ADMIN_ADJUST</option>
      </select>
      <input v-model="filters.bizNo" type="text" placeholder="业务单号" />
      <input v-model="filters.refBizNo" type="text" placeholder="关联单号" />
      <button class="btn btn-primary" type="submit" :disabled="loading">查询</button>
    </form>

    <ErrorBanner :message="error" retryable @retry="loadFlows" />
    <LoadingState v-if="loading" label="正在加载库存流水..." />

    <template v-else>
      <EmptyState v-if="!flows.length" title="暂无库存流水" description="调整库存或下单后会生成流水。" />

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>类型</th>
              <th>业务单号</th>
              <th>SKU</th>
              <th>数量</th>
              <th>可售变化</th>
              <th>锁定变化</th>
              <th>可售 before → after</th>
              <th>锁定 before → after</th>
              <th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="flow in flows" :key="flow.id">
              <td class="table-muted">{{ formatDateTime(flow.createdAt) }}</td>
              <td><span class="flow-type">{{ flow.bizType }}</span></td>
              <td>
                {{ flow.bizNo }}
                <div v-if="flow.refBizNo" class="table-muted">关联 {{ flow.refBizNo }}</div>
              </td>
              <td>{{ flow.skuId }}</td>
              <td>{{ flow.quantity }}</td>
              <td :class="flow.changeAvailable > 0 ? 'positive' : (flow.changeAvailable < 0 ? 'negative' : '')">
                {{ flow.changeAvailable > 0 ? '+' : '' }}{{ flow.changeAvailable }}
              </td>
              <td :class="flow.changeLocked > 0 ? 'positive' : (flow.changeLocked < 0 ? 'negative' : '')">
                {{ flow.changeLocked > 0 ? '+' : '' }}{{ flow.changeLocked }}
              </td>
              <td>{{ flow.availableBefore }} → {{ flow.availableAfter }}</td>
              <td>{{ flow.lockedBefore }} → {{ flow.lockedAfter }}</td>
              <td class="table-muted">{{ flow.remark || '-' }}</td>
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
.flows-toolbar {
  grid-template-columns: 100px 180px 1fr 1fr auto;
}

.flow-type {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
}

.positive {
  color: var(--success);
  font-weight: 700;
}

.negative {
  color: var(--danger);
  font-weight: 700;
}

@media (max-width: 900px) {
  .flows-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
