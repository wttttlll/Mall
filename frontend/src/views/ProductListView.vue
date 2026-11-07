<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import ProductCard from '../components/ProductCard.vue'
import { favoriteApi } from '../api/favorite'
import { productApi } from '../api/product'
import { useAuthStore } from '../stores/auth'
import { formatMoney } from '../utils/format'

const auth = useAuthStore()
const products = ref([])
const categories = ref([])
const showcaseProducts = ref([])
const showcaseTotal = ref(0)
const loading = ref(false)
const showcaseLoading = ref(true)
const error = ref('')
const showcaseError = ref('')
const favoriteError = ref('')
const favoriteNotice = ref('')
const favoriteIds = ref(new Set())
const favoriteBusyId = ref(null)
const page = reactive({ pageNum: 1, pageSize: 12, total: 0 })
const filters = reactive({ keyword: '', categoryId: '' })
const allProductsSection = ref(null)
const newProductsSection = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))

const categoryOptions = computed(() => {
  const idsWithProducts = new Set(showcaseProducts.value.map((product) => product.categoryId))
  const options = []
  const walk = (nodes, depth) => {
    for (const node of nodes || []) {
      if (idsWithProducts.has(node.id)) options.push({ ...node, depth })
      walk(node.children, depth + 1)
    }
  }
  walk(categories.value, 0)
  return options
})

const categoryIcons = {
  数码科技: '⌘',
  家居生活: '⌂',
  服饰鞋包: '◇',
  运动户外: '⛰',
  个护美妆: '✦',
  办公文具: '✎'
}

const homeCategories = computed(() =>
  categories.value.slice(0, 6).map((category) => ({
    ...category,
    icon: categoryIcons[category.name] || '◆',
    count: showcaseProducts.value.filter((product) => product.categoryId === category.id).length
  }))
)

function pickMixed(list, count) {
  const groups = new Map()
  for (const product of list) {
    const key = product.categoryId || 0
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(product)
  }
  const result = []
  let round = 0
  while (result.length < count) {
    let added = false
    for (const group of groups.values()) {
      if (group[round] && result.length < count) {
        result.push(group[round])
        added = true
      }
    }
    if (!added) break
    round += 1
  }
  return result
}

const heroProducts = computed(() => showcaseProducts.value.slice(0, 3))
const featuredProducts = computed(() => pickMixed(showcaseProducts.value, 8))
const newProducts = computed(() =>
  pickMixed([...showcaseProducts.value].sort((left, right) => right.id - left.id), 8)
)
const showcaseCount = computed(() => showcaseTotal.value || page.total)

async function loadCategories() {
  try {
    categories.value = await productApi.categories()
  } catch {
    categories.value = []
  }
}

async function loadShowcase() {
  showcaseLoading.value = true
  showcaseError.value = ''
  try {
    const data = await productApi.list({ pageNum: 1, pageSize: 100 })
    showcaseProducts.value = data.list || []
    showcaseTotal.value = data.total || 0
    await loadFavoriteStatus(showcaseProducts.value)
  } catch (err) {
    showcaseError.value = err.message || '精选商品加载失败'
  } finally {
    showcaseLoading.value = false
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
    await loadFavoriteStatus(products.value)
  } catch (err) {
    error.value = err.message || '商品加载失败'
  } finally {
    loading.value = false
  }
}

async function loadFavoriteStatus(items = []) {
  favoriteError.value = ''
  if (!auth.isAuthenticated) {
    favoriteIds.value = new Set()
    return
  }
  if (!items.length) return
  try {
    const data = await favoriteApi.status(items.map((product) => product.id))
    const next = new Set(favoriteIds.value)
    for (const id of data.favoriteSpuIds || []) next.add(id)
    favoriteIds.value = next
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
  scrollToAll()
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadProducts()
}

function selectCategory(category) {
  filters.categoryId = category.id
  page.pageNum = 1
  loadProducts()
  scrollToAll()
}

function scrollToAll() {
  nextTick(() => allProductsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function scrollToNew() {
  nextTick(() => newProductsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadShowcase()])
  await loadProducts()
})
</script>

<template>
  <div class="container page-section home-page">
    <section class="home-hero">
      <div class="hero-copy">
        <span class="hero-eyebrow">MALLAGENT · CURATED FOR EVERYDAY</span>
        <h1>把喜欢的好物<br /><span>带回家</span></h1>
        <p>精选数码、家居、穿搭与户外好物，让每一次日常都有新的灵感。</p>
        <div class="hero-actions">
          <button class="btn btn-hero-primary btn-lg" type="button" @click="scrollToAll">探索好物</button>
          <button class="hero-text-button" type="button" @click="scrollToNew">查看新品 <span>→</span></button>
        </div>
        <div class="hero-stats">
          <div><strong>{{ showcaseCount }}</strong><span>精选商品</span></div>
          <div><strong>6</strong><span>生活分类</span></div>
          <div><strong>售后</strong><span>完整流程</span></div>
        </div>
      </div>

      <div class="hero-visual" aria-hidden="true">
        <div class="hero-orbit orbit-one"></div>
        <div class="hero-orbit orbit-two"></div>
        <div class="hero-spotlight"></div>
        <article
          v-for="(product, index) in heroProducts"
          :key="product.id"
          class="hero-floating-card"
          :class="`float-${index + 1}`"
        >
          <img v-if="product.mainImage" :src="product.mainImage" :alt="product.name" />
          <div>
            <span>{{ product.categoryName }}</span>
            <strong>{{ product.name }}</strong>
            <em>{{ formatMoney(product.minPrice) }} 起</em>
          </div>
        </article>
        <div v-if="!heroProducts.length" class="hero-visual-empty">精选好物正在上架</div>
      </div>
    </section>

    <ErrorBanner :message="showcaseError" retryable @retry="loadShowcase" />
    <ErrorBanner :message="favoriteError" />
    <div v-if="favoriteNotice" class="success-banner">{{ favoriteNotice }}</div>

    <section v-if="homeCategories.length" class="home-section">
      <div class="home-section-heading">
        <div>
          <span class="eyebrow">SHOP BY CATEGORY</span>
          <h2>探索你的生活方式</h2>
          <p>从六大精选分类开始，发现更合心意的日常好物。</p>
        </div>
      </div>
      <div class="category-grid">
        <button
          v-for="(category, index) in homeCategories"
          :key="category.id"
          type="button"
          class="category-card"
          :class="`tone-${index + 1}`"
          @click="selectCategory(category)"
        >
          <span class="category-icon">{{ category.icon }}</span>
          <span class="category-info">
            <strong>{{ category.name }}</strong>
            <small>{{ category.count }} 件精选</small>
          </span>
          <span class="category-arrow">↗</span>
        </button>
      </div>
    </section>

    <section v-if="showcaseLoading || featuredProducts.length" class="home-section">
      <div class="home-section-heading">
        <div>
          <span class="eyebrow">CURATED SELECTION</span>
          <h2>精选好物</h2>
          <p>从品质与设计出发，挑出值得长期陪伴的日常选择。</p>
        </div>
        <button class="section-text-link" type="button" @click="scrollToAll">查看全部 →</button>
      </div>
      <LoadingState v-if="showcaseLoading" label="正在挑选好物..." />
      <div v-else class="product-grid">
        <ProductCard
          v-for="product in featuredProducts"
          :key="product.id"
          :product="product"
          :favorite="favoriteIds.has(product.id)"
          :favorite-busy="favoriteBusyId === product.id"
          @toggle-favorite="toggleFavorite"
        />
      </div>
    </section>

    <section ref="newProductsSection" class="home-section new-section">
      <div class="home-section-heading">
        <div>
          <span class="eyebrow">NEW ARRIVALS</span>
          <h2>新品推荐</h2>
          <p>刚刚抵达 MallAgent 的新鲜灵感，先人一步发现。</p>
        </div>
        <button class="section-text-link" type="button" @click="scrollToAll">更多新品 →</button>
      </div>
      <LoadingState v-if="showcaseLoading" label="正在加载新品..." />
      <div v-else-if="newProducts.length" class="product-grid">
        <ProductCard
          v-for="product in newProducts"
          :key="product.id"
          :product="product"
          :favorite="favoriteIds.has(product.id)"
          :favorite-busy="favoriteBusyId === product.id"
          @toggle-favorite="toggleFavorite"
        />
      </div>
    </section>

    <section ref="allProductsSection" class="home-section all-products-section">
      <div class="home-section-heading all-heading">
        <div>
          <span class="eyebrow">ALL PRODUCTS</span>
          <h2>全部商品</h2>
          <p>搜索、筛选，从整个 MallAgent 选品库找到你的心仪好物。</p>
        </div>
        <span class="catalog-count">{{ page.total }} 件商品</span>
      </div>

      <form class="filter-bar home-filter" @submit.prevent="search">
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
          <button
            class="btn btn-ghost"
            type="button"
            @click="filters.keyword = ''; filters.categoryId = ''; search()"
          >
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
    </section>
  </div>
</template>

<style scoped>
.home-page {
  padding-top: 24px;
}

.home-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 32px;
  min-height: 430px;
  padding: 48px;
  overflow: hidden;
  border-radius: 30px;
  background:
    radial-gradient(circle at 88% 12%, rgba(96, 165, 250, 0.35), transparent 34%),
    radial-gradient(circle at 4% 100%, rgba(14, 165, 233, 0.18), transparent 38%),
    linear-gradient(135deg, #081a38 0%, #0e2e63 52%, #12458a 100%);
  color: #fff;
  box-shadow: 0 24px 56px rgba(15, 45, 95, 0.2);
}

.home-hero::before,
.home-hero::after {
  content: '';
  position: absolute;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  pointer-events: none;
}

.home-hero::before {
  width: 480px;
  height: 480px;
  right: -170px;
  top: -220px;
}

.home-hero::after {
  width: 650px;
  height: 650px;
  left: -380px;
  bottom: -470px;
}

.hero-copy {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 570px;
}

.hero-eyebrow {
  color: #9dd6ff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.19em;
}

.hero-copy h1 {
  margin: 18px 0 16px;
  font-size: clamp(38px, 5vw, 62px);
  line-height: 1.08;
  letter-spacing: -0.055em;
  color: #fff;
}

.hero-copy h1 span {
  color: #93c5fd;
}

.hero-copy p {
  max-width: 450px;
  margin: 0;
  color: rgba(226, 238, 255, 0.74);
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 26px;
}

.btn-hero-primary {
  border-color: #fff;
  background: #fff;
  color: #0c2b5b;
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.16);
}

.btn-hero-primary:not(:disabled):hover {
  background: #eaf4ff;
  border-color: #eaf4ff;
}

.hero-text-button {
  padding: 8px 0;
  border: 0;
  background: transparent;
  color: rgba(230, 242, 255, 0.82);
  font-weight: 700;
  font-size: 13px;
}

.hero-text-button:hover {
  color: #fff;
}

.hero-text-button span {
  margin-left: 4px;
  color: #93c5fd;
}

.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin-top: 38px;
}

.hero-stats div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.hero-stats strong {
  color: #fff;
  font-size: 23px;
  letter-spacing: -0.03em;
}

.hero-stats span {
  color: rgba(202, 222, 248, 0.58);
  font-size: 11px;
}

.hero-visual {
  position: relative;
  z-index: 1;
  min-height: 330px;
}

.hero-orbit {
  position: absolute;
  border: 1px solid rgba(147, 197, 253, 0.22);
  border-radius: 50%;
}

.orbit-one {
  width: 330px;
  height: 330px;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.orbit-two {
  width: 245px;
  height: 245px;
  top: 62px;
  left: 50%;
  transform: translateX(-50%);
}

.hero-spotlight {
  position: absolute;
  top: 105px;
  left: 50%;
  width: 170px;
  height: 170px;
  border-radius: 46px;
  transform: translateX(-50%) rotate(12deg);
  background: linear-gradient(140deg, rgba(96, 165, 250, 0.32), rgba(255, 255, 255, 0.08));
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 20px 60px rgba(3, 20, 50, 0.3);
}

.hero-floating-card {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 225px;
  padding: 9px;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--text-strong);
  box-shadow: 0 18px 40px rgba(1, 14, 38, 0.24);
  backdrop-filter: blur(12px);
}

.hero-floating-card img {
  width: 54px;
  height: 54px;
  flex: none;
  border-radius: 10px;
  object-fit: cover;
}

.hero-floating-card div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hero-floating-card span {
  color: var(--text-soft);
  font-size: 9px;
}

.hero-floating-card strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.hero-floating-card em {
  color: var(--price);
  font-style: normal;
  font-size: 11px;
  font-weight: 800;
}

.float-1 {
  top: 30px;
  left: 0;
  transform: rotate(-4deg);
}

.float-2 {
  top: 140px;
  right: 0;
  transform: rotate(4deg);
}

.float-3 {
  bottom: 24px;
  left: 15%;
  transform: rotate(-2deg);
}

.hero-visual-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
}

.home-section {
  margin-top: 46px;
  scroll-margin-top: 88px;
}

.home-section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.home-section-heading h2 {
  margin: 6px 0 6px;
  color: var(--text-strong);
  font-size: clamp(24px, 3vw, 32px);
  letter-spacing: -0.04em;
}

.home-section-heading p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.section-text-link {
  flex: none;
  padding: 8px 0;
  border: 0;
  background: transparent;
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
}

.section-text-link:hover {
  color: var(--primary-strong);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.category-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 13px;
  border: 1px solid var(--border);
  border-radius: 17px;
  background: var(--surface);
  color: var(--text-strong);
  text-align: left;
  box-shadow: var(--shadow-sm);
  transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.category-card:hover {
  transform: translateY(-3px);
  border-color: #b9cdf1;
  box-shadow: var(--shadow-md);
}

.category-icon {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 12px;
  font-size: 18px;
  font-weight: 800;
}

.category-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.category-info strong {
  font-size: 13px;
  white-space: nowrap;
}

.category-info small {
  color: var(--text-soft);
  font-size: 10px;
  white-space: nowrap;
}

.category-arrow {
  position: absolute;
  top: 10px;
  right: 10px;
  color: var(--text-soft);
  font-size: 12px;
}

.tone-1 .category-icon {
  color: #2563eb;
  background: #eaf3ff;
}

.tone-2 .category-icon {
  color: #9a6b4f;
  background: #fbf0e7;
}

.tone-3 .category-icon {
  color: #b34b77;
  background: #fceef4;
}

.tone-4 .category-icon {
  color: #0f766e;
  background: #e9fbf7;
}

.tone-5 .category-icon {
  color: #9b4e9f;
  background: #f9effb;
}

.tone-6 .category-icon {
  color: #64748b;
  background: #f1f5f9;
}

.new-section {
  padding-top: 2px;
}

.all-products-section {
  padding-top: 2px;
}

.all-heading {
  margin-bottom: 16px;
}

.catalog-count {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.home-filter {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 220px auto;
  gap: 10px;
  padding: 12px;
  margin-bottom: 22px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

@media (max-width: 1080px) {
  .category-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .home-hero {
    grid-template-columns: 1fr;
    min-height: 0;
    padding: 34px 28px;
  }

  .hero-copy {
    max-width: none;
  }

  .hero-visual {
    min-height: 230px;
  }

  .float-1 {
    top: 8px;
  }

  .float-2 {
    top: 88px;
  }

  .float-3 {
    bottom: 8px;
  }
}

@media (max-width: 620px) {
  .home-page {
    padding-top: 14px;
  }

  .home-hero {
    border-radius: 22px;
    padding: 28px 20px;
  }

  .hero-copy h1 {
    font-size: 38px;
  }

  .hero-stats {
    gap: 20px;
    margin-top: 26px;
  }

  .hero-visual {
    display: none;
  }

  .home-section {
    margin-top: 34px;
  }

  .home-section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .category-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-filter {
    grid-template-columns: 1fr;
  }
}
</style>
