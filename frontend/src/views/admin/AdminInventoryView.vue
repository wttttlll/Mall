<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import { adminInventoryApi } from '../../api/adminInventory'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime } from '../../utils/format'
import { createIdempotencyKey } from '../../utils/idempotency'

const auth = useAuthStore()
const stocks = ref([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const filterSkuId = ref('')
const showAdjust = ref(false)

const form = reactive({ skuId: '', delta: '', remark: '' })
const idempotencyKey = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))
const canAdjust = computed(() => auth.hasPermission('inventory:stock:adjust'))

async function loadStocks() {
  loading.value = true
  error.value = ''
  try {
    const data = await adminInventoryApi.stocks({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      skuId: filterSkuId.value.trim() || undefined
    })
    stocks.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '库存加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.pageNum = 1
  loadStocks()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadStocks()
}

function openAdjust(stock = null) {
  form.skuId = stock?.skuId ?? (filterSkuId.value.trim() || '')
  form.delta = ''
  form.remark = ''
  idempotencyKey.value = createIdempotencyKey('inv')
  showAdjust.value = true
  error.value = ''
  message.value = ''
}

function closeAdjust() {
  showAdjust.value = false
}

async function saveAdjust() {
  error.value = ''
  const skuId = Number(form.skuId)
  const delta = Number(form.delta)
  if (!Number.isInteger(skuId) || skuId <= 0) {
    error.value = '请填写正确的 SKU ID'
    return
  }
  if (!Number.isInteger(delta) || delta === 0) {
    error.value = '调整数量必须是非 0 整数'
    return
  }
  if ((form.remark || '').length > 255) {
    error.value = '备注不能超过 255 个字符'
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    await adminInventoryApi.adjust(
      { skuId, delta, remark: form.remark.trim() || null },
      idempotencyKey.value
    )
    message.value = `SKU ${skuId} 库存调整成功`
    showAdjust.value = false
    await loadStocks()
  } catch (err) {
    error.value = err.message || '库存调整失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadStocks)
</script>

<template>
  <div>
    <div class="admin-page-heading">
      <div>
        <span class="eyebrow">Inventory</span>
        <h1>库存管理</h1>
      </div>
      <button v-if="canAdjust" class="btn btn-primary" type="button" @click="openAdjust()">按 SKU 调整</button>
    </div>

    <form class="admin-toolbar inventory-toolbar" @submit.prevent="search">
      <input v-model="filterSkuId" type="number" min="1" placeholder="按 SKU ID 筛选" />
      <button class="btn btn-primary" type="submit" :disabled="loading">查询</button>
    </form>

    <ErrorBanner :message="error" retryable @retry="loadStocks" />
    <div v-if="message" class="success-banner">{{ message }}</div>
    <div v-if="!canAdjust" class="admin-readonly-note">当前账号只能查看库存，不能调整。</div>

    <section v-if="showAdjust" class="admin-section-card">
      <div class="admin-section-title">
        <h2>调整库存</h2>
        <button class="btn btn-ghost btn-sm" type="button" @click="closeAdjust">关闭</button>
      </div>
      <form class="form-stack" @submit.prevent="saveAdjust">
        <div class="form-grid three">
          <label class="field">
            <span>SKU ID *</span>
            <input v-model="form.skuId" type="number" min="1" :disabled="saving" />
          </label>
          <label class="field">
            <span>调整数量 *（正数入库，负数出库）</span>
            <input v-model="form.delta" type="number" :disabled="saving" />
          </label>
          <label class="field">
            <span>备注</span>
            <input v-model="form.remark" type="text" maxlength="255" :disabled="saving" />
          </label>
        </div>
        <p class="admin-hint">调整会经过现有库存服务并写入库存流水；同一表单重试使用同一幂等键。</p>
        <div class="form-actions">
          <button class="btn btn-primary" type="submit" :disabled="saving">
            {{ saving ? '提交中...' : '确认调整' }}
          </button>
          <button class="btn btn-ghost" type="button" :disabled="saving" @click="closeAdjust">取消</button>
        </div>
      </form>
    </section>

    <LoadingState v-if="loading" label="正在加载库存..." />

    <template v-else>
      <EmptyState
        v-if="!stocks.length"
        title="暂无库存记录"
        description="SKU 首次正向调整后才会生成库存记录。"
      >
        <button v-if="canAdjust" class="btn btn-primary" type="button" @click="openAdjust()">按 SKU 调整</button>
      </EmptyState>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>SKU</th>
              <th>可售库存</th>
              <th>锁定库存</th>
              <th>总库存</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="stock in stocks" :key="stock.skuId">
              <td>
                <strong>{{ stock.skuCode || `SKU ${stock.skuId}` }}</strong>
                <div class="table-muted">{{ stock.spuName || '未关联商品' }} · ID {{ stock.skuId }}</div>
              </td>
              <td><strong>{{ stock.availableStock }}</strong></td>
              <td>{{ stock.lockedStock }}</td>
              <td>{{ stock.totalStock }}</td>
              <td class="table-muted">{{ formatDateTime(stock.updatedAt) }}</td>
              <td>
                <div class="table-actions">
                  <button v-if="canAdjust" class="btn btn-ghost btn-sm" type="button" @click="openAdjust(stock)">
                    调整
                  </button>
                  <RouterLink class="btn btn-ghost btn-sm" :to="`/admin/inventory/flows?skuId=${stock.skuId}`">
                    流水
                  </RouterLink>
                </div>
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
.inventory-toolbar {
  grid-template-columns: minmax(180px, 1fr) auto;
}

.form-grid.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 760px) {
  .form-grid.three {
    grid-template-columns: 1fr;
  }
}
</style>
