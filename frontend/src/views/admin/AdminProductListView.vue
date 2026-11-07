<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import { adminProductApi } from '../../api/adminProduct'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime, productStatusClass, productStatusText } from '../../utils/format'

const auth = useAuthStore()
const products = ref([])
const categories = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const busyId = ref(null)
const filters = reactive({ keyword: '', status: '', categoryId: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))
const categoryOptions = computed(() => {
  const options = []
  const walk = (nodes, depth) => {
    for (const node of nodes || []) {
      options.push({ ...node, depth })
      walk(node.children, depth + 1)
    }
  }
  walk(categories.value, 0)
  return options
})
const canCreate = computed(() => auth.hasPermission('product:spu:create'))
const canUpdate = computed(() => auth.hasPermission('product:spu:update'))
const canOnShelf = computed(() => auth.hasPermission('product:spu:on-shelf'))
const canOffShelf = computed(() => auth.hasPermission('product:spu:off-shelf'))

async function loadCategories() {
  try {
    categories.value = await adminProductApi.categories()
  } catch {
    categories.value = []
  }
}

async function loadProducts() {
  loading.value = true
  error.value = ''
  try {
    const data = await adminProductApi.spus({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      status: filters.status || undefined,
      categoryId: filters.categoryId || undefined,
      keyword: filters.keyword.trim() || undefined
    })
    products.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '商品加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.pageNum = 1
  loadProducts()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadProducts()
}

async function runStatusAction(product, action, label) {
  if (!window.confirm(`确认${label}商品「${product.name}」吗？`)) return
  busyId.value = product.id
  error.value = ''
  message.value = ''
  try {
    await action(product.id)
    message.value = `商品「${product.name}」已${label}`
    await loadProducts()
  } catch (err) {
    error.value = err.message || `${label}失败`
  } finally {
    busyId.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadProducts()])
})
</script>

<template>
  <div>
    <div class="admin-page-heading">
      <div>
        <span class="eyebrow">Products</span>
        <h1>商品管理</h1>
      </div>
      <RouterLink v-if="canCreate" class="btn btn-primary" to="/admin/products/new">新增商品</RouterLink>
    </div>

    <form class="admin-toolbar" @submit.prevent="search">
      <input v-model="filters.keyword" type="search" placeholder="搜索商品名称" />
      <select v-model="filters.status" @change="search">
        <option value="">全部状态</option>
        <option value="DRAFT">草稿</option>
        <option value="ON_SHELF">已上架</option>
        <option value="OFF_SHELF">已下架</option>
      </select>
      <select v-model="filters.categoryId" @change="search">
        <option value="">全部分类</option>
        <option v-for="item in categoryOptions" :key="item.id" :value="item.id">
          {{ '　'.repeat(item.depth) }}{{ item.name }}
        </option>
      </select>
      <button class="btn btn-primary" type="submit" :disabled="loading">搜索</button>
    </form>

    <ErrorBanner :message="error" retryable @retry="loadProducts" />
    <div v-if="message" class="success-banner">{{ message }}</div>
    <div v-if="!canCreate" class="admin-readonly-note">当前账号为只读权限，不能新增、编辑或上下架商品。</div>

    <LoadingState v-if="loading" label="正在加载商品..." />

    <template v-else>
      <EmptyState v-if="!products.length" title="暂无商品" description="点击右上角新增商品，或调整筛选条件。">
        <RouterLink v-if="canCreate" class="btn btn-primary" to="/admin/products/new">新增商品</RouterLink>
      </EmptyState>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>商品</th>
              <th>分类 / 品牌</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="product in products" :key="product.id">
              <td>
                <div class="product-cell">
                  <img v-if="product.mainImage" :src="product.mainImage" :alt="product.name" class="admin-image" />
                  <div>
                    <strong>{{ product.name }}</strong>
                    <div class="table-muted">ID {{ product.id }} · {{ product.subtitle || '无副标题' }}</div>
                  </div>
                </div>
              </td>
              <td>
                {{ product.categoryName || '-' }}
                <div class="table-muted">{{ product.brandName || '无品牌' }}</div>
              </td>
              <td>
                <span class="status-badge" :class="productStatusClass(product.status)">
                  {{ productStatusText(product.status) }}
                </span>
              </td>
              <td class="table-muted">{{ formatDateTime(product.updatedAt) }}</td>
              <td>
                <div class="table-actions">
                  <RouterLink class="btn btn-ghost btn-sm" :to="`/admin/products/${product.id}`">
                    {{ canUpdate && product.status !== 'ON_SHELF' ? '编辑' : '查看' }}
                  </RouterLink>
                  <button
                    v-if="product.status === 'DRAFT' && canOnShelf"
                    class="btn btn-primary btn-sm"
                    type="button"
                    :disabled="busyId === product.id"
                    @click="runStatusAction(product, adminProductApi.onShelf, '上架')"
                  >
                    上架
                  </button>
                  <button
                    v-if="product.status === 'ON_SHELF' && canOffShelf"
                    class="btn btn-danger-ghost btn-sm"
                    type="button"
                    :disabled="busyId === product.id"
                    @click="runStatusAction(product, adminProductApi.offShelf, '下架')"
                  >
                    下架
                  </button>
                  <template v-if="product.status === 'OFF_SHELF'">
                    <button
                      v-if="canOnShelf"
                      class="btn btn-primary btn-sm"
                      type="button"
                      :disabled="busyId === product.id"
                      @click="runStatusAction(product, adminProductApi.onShelf, '上架')"
                    >
                      上架
                    </button>
                    <button
                      v-if="canUpdate"
                      class="btn btn-ghost btn-sm"
                      type="button"
                      :disabled="busyId === product.id"
                      @click="runStatusAction(product, adminProductApi.toDraft, '转草稿')"
                    >
                      转草稿
                    </button>
                  </template>
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
.product-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
}

.product-cell strong {
  display: block;
  color: var(--text-strong);
}
</style>
