<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import ProductCard from '../components/ProductCard.vue'
import { favoriteApi } from '../api/favorite'
import { productApi } from '../api/product'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const products = ref([])
const categories = ref([])
const loading = ref(false)
const error = ref('')
const favoriteError = ref('')
const favoriteNotice = ref('')
const favoriteIds = ref(new Set())
const favoriteBusyId = ref(null)
const page = reactive({ pageNum: 1, pageSize: 12, total: 0 })
const filters = reactive({ keyword: '', categoryId: '' })

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

async function loadCategories() {
  try {
    categories.value = await productApi.categories()
  } catch {
    categories.value = []
  }
}

async function loadProducts() {
  loading.value = true
  error.value = ''
  favoriteNotice.value = ''
  try {
    const data = await productApi.list({
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      categoryId: filters.categoryId || undefined,
      keyword: filters.keyword.trim() || undefined
    })
    products.value = data.list || []
    page.total = data.total || 0
    await loadFavoriteStatus()
  } catch (err) {
    error.value = err.message || '商品加载失败'
  } finally {
    loading.value = false
  }
}

async function loadFavoriteStatus() {
  favoriteError.value = ''
  if (!auth.isAuthenticated || !products.value.length) {
    favoriteIds.value = new Set()
    return
  }
  try {
    const data = await favoriteApi.status(products.value.map((product) => product.id))
    favoriteIds.value = new Set(data.favoriteSpuIds || [])
  } catch (err) {
    favoriteError.value = err.message || '收藏状态加载失败'
  }
}

async function toggleFavorite(product) {
  if (favoriteBusyId.value) return
  favoriteBusyId.value = product.id
  favoriteError.value = ''
  favoriteNotice.value = ''
  try {
    const active = favoriteIds.value.has(product.id)
    if (active) {
      await favoriteApi.remove(product.id)
      const next = new Set(favoriteIds.value)
      next.delete(product.id)
      favoriteIds.value = next
      favoriteNotice.value = `已取消收藏「${product.name}」`
    } else {
      await favoriteApi.add(product.id)
      favoriteIds.value = new Set([...favoriteIds.value, product.id])
      favoriteNotice.value = `已收藏「${product.name}」`
    }
  } catch (err) {
    favoriteError.value = err.message || '收藏操作失败'
  } finally {
    favoriteBusyId.value = null
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

onMounted(() => {
  loadCategories()
  loadProducts()
})
</script>

<template>
  <div class="container page-section">
    <section class="hero">
      <div>
        <span class="eyebrow">MallAgent Store</span>
        <h1>发现值得买的好物</h1>
        <p>从浏览到下单、模拟支付，一条完整的购物链路。</p>
      </div>
      <div class="hero-stat">
        <span>商品总数</span>
        <strong>{{ page.total }}</strong>
      </div>
    </section>

    <form class="filter-bar" @submit.prevent="search">
      <input v-model="filters.keyword" type="search" placeholder="搜索商品名称或关键词" />
      <select v-model="filters.categoryId" @change="search">
        <option value="">全部分类</option>
        <option v-for="item in categoryOptions" :key="item.id" :value="item.id">
          {{ '　'.repeat(item.depth) }}{{ item.name }}
        </option>
      </select>
      <button class="btn btn-primary" type="submit">搜索</button>
    </form>

    <ErrorBanner :message="error" retryable @retry="loadProducts" />
    <ErrorBanner :message="favoriteError" retryable @retry="loadFavoriteStatus" />
    <div v-if="favoriteNotice" class="success-banner">{{ favoriteNotice }}</div>

    <LoadingState v-if="loading" label="正在加载商品..." />

    <template v-else>
      <div v-if="products.length" class="product-grid">
        <ProductCard
          v-for="product in products"
          :key="product.id"
          :product="product"
          :favorite="favoriteIds.has(product.id)"
          :favorite-busy="favoriteBusyId === product.id"
          @toggle-favorite="toggleFavorite"
        />
      </div>
      <EmptyState v-else title="没有找到商品" description="换个关键词或分类试试。">
        <button class="btn btn-ghost" type="button" @click="filters.keyword = ''; filters.categoryId = ''; search()">
          清除筛选
        </button>
      </EmptyState>

      <div v-if="page.total > page.pageSize" class="pagination">
        <button class="btn btn-ghost btn-sm" :disabled="page.pageNum <= 1" @click="goPage(page.pageNum - 1)">
          上一页
        </button>
        <span>第 {{ page.pageNum }} / {{ totalPages }} 页</span>
        <button
          class="btn btn-ghost btn-sm"
          :disabled="page.pageNum >= totalPages"
          @click="goPage(page.pageNum + 1)"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 34px;
  margin-bottom: 22px;
  border-radius: var(--radius-xl);
  background:
    radial-gradient(circle at 88% 15%, rgba(147, 197, 253, 0.45), transparent 35%),
    linear-gradient(135deg, #f7faff, #eef4ff 55%, #f8fbff);
  border: 1px solid #dbe7ff;
}

.eyebrow {
  color: var(--primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero h1 {
  margin: 8px 0 8px;
  font-size: clamp(26px, 4vw, 42px);
  letter-spacing: -0.04em;
  color: var(--text-strong);
}

.hero p {
  margin: 0;
  color: var(--text-muted);
}

.hero-stat {
  min-width: 120px;
  padding: 16px 20px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid #dbe7ff;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hero-stat span {
  color: var(--text-muted);
  font-size: 12px;
}

.hero-stat strong {
  color: var(--text-strong);
  font-size: 26px;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 220px auto;
  gap: 10px;
  padding: 12px;
  margin-bottom: 22px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 28px;
  color: var(--text-muted);
  font-size: 14px;
}

@media (max-width: 760px) {
  .hero {
    padding: 24px;
    align-items: flex-start;
  }

  .hero-stat {
    display: none;
  }

  .filter-bar {
    grid-template-columns: 1fr;
  }
}
</style>
