<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import FavoriteButton from '../components/FavoriteButton.vue'
import LoadingState from '../components/LoadingState.vue'
import StarRating from '../components/StarRating.vue'
import { cartApi } from '../api/cart'
import { favoriteApi } from '../api/favorite'
import { productApi } from '../api/product'
import { reviewApi } from '../api/review'
import { useAuthStore } from '../stores/auth'
import { formatDateTime, formatMoney } from '../utils/format'
import { createIdempotencyKey } from '../utils/idempotency'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const product = ref(null)
const loading = ref(true)
const error = ref('')
const selectedSkuId = ref(null)
const quantity = ref(1)
const adding = ref(false)
const buying = ref(false)
const notice = ref('')
const imageFailed = ref(false)
const favorite = ref(false)
const favoriteBusy = ref(false)
const favoriteError = ref('')
const favoriteNotice = ref('')
const reviews = ref([])
const averageRating = ref(0)
const reviewCount = ref(0)
const reviewsLoading = ref(false)
const reviewsError = ref('')
const reviewPage = reactive({ pageNum: 1, pageSize: 5, total: 0 })

const activeSkus = computed(() =>
  (product.value?.skus || []).filter((sku) => sku.status === 1)
)
const selectedSku = computed(() =>
  activeSkus.value.find((sku) => sku.id === selectedSkuId.value) || activeSkus.value[0] || null
)
const priceRange = computed(() => {
  const prices = activeSkus.value.map((sku) => Number(sku.price)).filter(Number.isFinite)
  if (!prices.length) return ''
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  return min === max ? formatMoney(min) : `${formatMoney(min)} - ${formatMoney(max)}`
})
const displayImage = computed(() => selectedSku.value?.imageUrl || product.value?.mainImage || '')
watch(displayImage, () => {
  imageFailed.value = false
})

async function loadProduct() {
  loading.value = true
  error.value = ''
  favoriteError.value = ''
  favoriteNotice.value = ''
  reviewsError.value = ''
  reviews.value = []
  averageRating.value = 0
  reviewCount.value = 0
  reviewPage.pageNum = 1
  try {
    product.value = await productApi.detail(route.params.id)
    selectedSkuId.value = activeSkus.value[0]?.id ?? null
    await Promise.all([loadFavorite(), loadReviews(true)])
  } catch (err) {
    error.value = err.message || '商品加载失败'
  } finally {
    loading.value = false
  }
}

async function loadFavorite() {
  favoriteError.value = ''
  if (!auth.isAuthenticated) {
    favorite.value = false
    return
  }
  try {
    const data = await favoriteApi.status([Number(route.params.id)])
    favorite.value = (data.favoriteSpuIds || []).includes(Number(route.params.id))
  } catch (err) {
    favoriteError.value = err.message || '收藏状态加载失败'
  }
}

async function toggleFavorite() {
  if (favoriteBusy.value) return
  if (!auth.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  favoriteBusy.value = true
  favoriteError.value = ''
  favoriteNotice.value = ''
  try {
    const data = favorite.value
      ? await favoriteApi.remove(product.value.id)
      : await favoriteApi.add(product.value.id)
    favorite.value = data.favorite
    favoriteNotice.value = data.favorite ? '已收藏该商品' : '已取消收藏'
  } catch (err) {
    favoriteError.value = err.message || '收藏操作失败'
  } finally {
    favoriteBusy.value = false
  }
}

async function loadReviews(reset = false) {
  if (reset) {
    reviewPage.pageNum = 1
  }
  reviewsLoading.value = true
  reviewsError.value = ''
  try {
    const data = await reviewApi.list(route.params.id, {
      pageNum: reviewPage.pageNum,
      pageSize: reviewPage.pageSize
    })
    const list = data.list || []
    reviews.value = reset ? list : [...reviews.value, ...list]
    averageRating.value = Number(data.averageRating ?? 0)
    reviewCount.value = Number(data.reviewCount ?? 0)
    reviewPage.total = Number(data.total ?? 0)
  } catch (err) {
    reviewsError.value = err.message || '评价加载失败'
  } finally {
    reviewsLoading.value = false
  }
}

async function loadMoreReviews() {
  if (reviewsLoading.value) return
  reviewPage.pageNum += 1
  await loadReviews(false)
}

function changeQuantity(delta) {
  quantity.value = Math.min(99, Math.max(1, quantity.value + delta))
}

function onQuantityInput(event) {
  const next = Number(event.target.value)
  quantity.value = Number.isFinite(next) ? Math.min(99, Math.max(1, Math.trunc(next))) : 1
  event.target.value = quantity.value
}

async function addToCart() {
  if (!selectedSku.value || adding.value) return
  if (!auth.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  adding.value = true
  notice.value = ''
  try {
    await cartApi.add(selectedSku.value.id, quantity.value, createIdempotencyKey('cart'))
    notice.value = `已将 ${quantity.value} 件商品加入购物车`
  } catch (err) {
    error.value = err.message || '加入购物车失败'
  } finally {
    adding.value = false
  }
}

async function buyNow() {
  if (!selectedSku.value || buying.value || adding.value) return
  if (!auth.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  buying.value = true
  error.value = ''
  notice.value = ''
  try {
    await cartApi.add(selectedSku.value.id, quantity.value, createIdempotencyKey('cart'))
    // 立即购买只结算当前 SKU：清空其他勾选，再勾选当前商品。
    await cartApi.selectAll(false)
    await cartApi.setSelected(selectedSku.value.id, true)
    await router.push('/checkout')
  } catch (err) {
    error.value = err.message || '立即购买失败'
  } finally {
    buying.value = false
  }
}

onMounted(loadProduct)
watch(() => route.params.id, (next, previous) => {
  if (next && next !== previous) loadProduct()
})
</script>

<template>
  <div class="container page-section">
    <RouterLink to="/products" class="back-link">← 返回商品列表</RouterLink>

    <LoadingState v-if="loading" label="正在加载商品详情..." />
    <ErrorBanner v-else-if="error && !product" :message="error" retryable @retry="loadProduct" />

    <template v-else-if="product">
      <div class="detail-layout">
        <div class="detail-media">
          <div class="detail-image-wrap">
            <img
              v-if="displayImage && !imageFailed"
              :src="displayImage"
              :alt="product.name"
              class="detail-image"
              @error="imageFailed = true"
            />
            <div v-else class="detail-image placeholder">
              {{ imageFailed ? '图片暂不可用' : '暂无图片' }}
            </div>
          </div>
          <div v-if="product.images?.length > 1" class="thumb-row">
            <img
              v-for="image in product.images.slice(0, 5)"
              :key="image"
              :src="image"
              :alt="product.name"
              class="thumb"
            />
          </div>
        </div>

        <section class="detail-info">
          <div class="detail-meta">
            <span v-if="product.categoryName" class="tag">{{ product.categoryName }}</span>
            <span v-if="product.brandName" class="tag tag-muted">{{ product.brandName }}</span>
          </div>
          <div class="detail-title-row">
            <h1>{{ product.name }}</h1>
            <FavoriteButton
              show-label
              :active="favorite"
              :busy="favoriteBusy"
              @toggle="toggleFavorite"
            />
          </div>
          <p class="detail-subtitle">{{ product.subtitle }}</p>

          <div class="price-panel">
            <span class="price-label">当前价格</span>
            <strong class="detail-price">
              {{ selectedSku ? formatMoney(selectedSku.price) : priceRange || '价格待定' }}
            </strong>
            <span v-if="selectedSku?.marketPrice" class="market-price">
              {{ formatMoney(selectedSku.marketPrice) }}
            </span>
            <span class="stock-badge" :class="{ 'stock-empty': !selectedSku }">
              {{ selectedSku ? '现货' : '暂时缺货' }}
            </span>
          </div>

          <div class="option-block">
            <div class="option-label">选择规格</div>
            <div v-if="activeSkus.length" class="sku-list">
              <button
                v-for="sku in activeSkus"
                :key="sku.id"
                type="button"
                class="sku-option"
                :class="{ active: selectedSku?.id === sku.id }"
                @click="selectedSkuId = sku.id"
              >
                <span>{{ sku.specValues?.map((item) => item.value).join(' / ') || sku.skuCode }}</span>
                <strong>{{ formatMoney(sku.price) }}</strong>
              </button>
            </div>
            <p v-else class="text-muted">该商品暂无可用规格。</p>
          </div>

          <div class="option-block">
            <div class="option-label">购买数量</div>
            <div class="quantity-stepper">
              <button type="button" :disabled="quantity <= 1" @click="changeQuantity(-1)">−</button>
              <input :value="quantity" type="number" min="1" max="99" @change="onQuantityInput" />
              <button type="button" :disabled="quantity >= 99" @click="changeQuantity(1)">+</button>
            </div>
          </div>

          <ErrorBanner :message="error" />
          <ErrorBanner :message="favoriteError" />
          <div v-if="notice" class="success-banner">
            {{ notice }}
            <RouterLink to="/cart">去购物车结算 →</RouterLink>
          </div>
          <div v-if="favoriteNotice" class="success-banner">{{ favoriteNotice }}</div>

          <div class="purchase-actions">
            <button
              class="btn btn-primary btn-lg"
              type="button"
              :disabled="!selectedSku || buying || adding"
              @click="buyNow"
            >
              {{ buying ? '正在前往结算...' : '立即购买' }}
            </button>
            <button
              class="btn btn-ghost btn-lg"
              type="button"
              :disabled="!selectedSku || adding || buying"
              @click="addToCart"
            >
              {{ adding ? '加入中...' : '加入购物车' }}
            </button>
          </div>
          <p class="purchase-note">支持模拟支付与整单售后 · 演示环境不产生真实交易</p>

          <div class="detail-description">
            <h2>商品详情</h2>
            <p>{{ product.description || '暂无商品描述。' }}</p>
          </div>
        </section>
      </div>

      <section class="surface-card review-section">
        <div class="review-section-head">
          <div>
            <span class="eyebrow">Reviews</span>
            <h2>商品评价</h2>
          </div>
          <div class="review-summary">
            <StarRating :model-value="averageRating" readonly show-value size="sm" />
            <span>{{ reviewCount }} 条评价</span>
          </div>
        </div>

        <ErrorBanner :message="reviewsError" retryable @retry="loadReviews(true)" />
        <LoadingState v-if="reviewsLoading && !reviews.length" label="正在加载评价..." />

        <EmptyState
          v-else-if="!reviews.length && !reviewsError"
          title="暂无商品评价"
          description="购买并完成订单后，可以在这里分享你的使用感受。"
        />

        <template v-else>
          <div class="review-list">
            <article v-for="review in reviews" :key="review.id" class="review-item">
              <div class="review-avatar">{{ (review.displayName || '匿').slice(0, 1) }}</div>
              <div class="review-main">
                <div class="review-meta">
                  <strong>{{ review.displayName }}</strong>
                  <StarRating :model-value="review.rating" readonly size="sm" />
                  <span class="review-time">{{ formatDateTime(review.createdAt) }}</span>
                </div>
                <p>{{ review.content || '用户未填写文字评价' }}</p>
              </div>
            </article>
          </div>

          <button
            v-if="reviews.length < reviewPage.total"
            class="btn btn-ghost review-more"
            type="button"
            :disabled="reviewsLoading"
            @click="loadMoreReviews"
          >
            {{ reviewsLoading ? '加载中...' : '查看更多评价' }}
          </button>
        </template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1fr);
  gap: 42px;
  align-items: start;
}

.detail-media {
  position: sticky;
  top: 88px;
}

.detail-image-wrap {
  aspect-ratio: 1 / 1;
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--surface-muted);
  border: 1px solid var(--border);
}

.detail-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.detail-image.placeholder {
  display: grid;
  place-items: center;
  color: var(--text-soft);
}

.thumb-row {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--border);
}

.detail-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-title-row h1 {
  margin: 10px 0 8px;
  font-size: clamp(26px, 3.4vw, 38px);
  letter-spacing: -0.035em;
  color: var(--text-strong);
}

.detail-title-row :deep(.favorite-btn) {
  margin-top: 14px;
  flex: none;
}

.detail-subtitle {
  margin: 0 0 22px;
  color: var(--text-muted);
  font-size: 15px;
}

.detail-meta {
  display: flex;
  gap: 8px;
}

.price-panel {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 20px 22px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #fff7f3, #fffaf8);
  border: 1px solid #ffe1d5;
  margin-bottom: 24px;
}

.price-label {
  color: var(--text-muted);
  font-size: 13px;
}

.detail-price {
  color: var(--price);
  font-size: 32px;
  letter-spacing: -0.03em;
}

.market-price {
  color: var(--text-soft);
  text-decoration: line-through;
  font-size: 14px;
}

.stock-badge {
  margin-left: auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #047857;
  font-size: 11px;
  font-weight: 800;
}

.stock-badge.stock-empty {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}

.purchase-actions {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 10px;
  margin-top: 4px;
}

.purchase-note {
  margin: 10px 0 0;
  color: var(--text-soft);
  font-size: 11px;
  text-align: center;
}

.option-block {
  margin-bottom: 22px;
}

.option-label {
  margin-bottom: 10px;
  color: var(--text-strong);
  font-weight: 700;
  font-size: 14px;
}

.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.sku-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  cursor: pointer;
  text-align: left;
}

.sku-option strong {
  color: var(--price);
  font-size: 14px;
}

.sku-option.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: 0 0 0 1px var(--primary) inset;
}

.detail-description {
  margin-top: 34px;
  padding-top: 24px;
  border-top: 1px solid var(--border);
}

.detail-description h2 {
  font-size: 18px;
  margin: 0 0 12px;
}

.detail-description p {
  color: var(--text-muted);
  white-space: pre-line;
  line-height: 1.8;
  margin: 0;
}

.review-section {
  margin-top: 28px;
  padding: 24px;
}

.review-section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.review-section-head h2 {
  margin: 5px 0 0;
  color: var(--text-strong);
  font-size: 21px;
}

.review-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.review-list {
  display: flex;
  flex-direction: column;
}

.review-item {
  display: flex;
  gap: 12px;
  padding: 18px 0;
  border-bottom: 1px solid var(--border);
}

.review-avatar {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--primary-soft);
  color: var(--primary);
  font-weight: 800;
}

.review-main {
  min-width: 0;
  flex: 1;
}

.review-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
}

.review-meta strong {
  color: var(--text-strong);
}

.review-time {
  margin-left: auto;
  color: var(--text-soft);
  font-size: 12px;
}

.review-main p {
  margin: 7px 0 0;
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-line;
}

.review-more {
  display: flex;
  margin: 18px auto 0;
}

@media (max-width: 860px) {
  .detail-layout {
    grid-template-columns: 1fr;
    gap: 26px;
  }

  .detail-media {
    position: static;
  }

  .review-section-head {
    align-items: flex-start;
  }
}

@media (max-width: 600px) {
  .detail-title-row {
    flex-direction: column;
    gap: 4px;
  }

  .detail-title-row :deep(.favorite-btn) {
    margin-top: 0;
  }

  .review-summary {
    align-items: flex-start;
  }

  .purchase-actions {
    grid-template-columns: 1fr;
  }

  .price-panel {
    flex-wrap: wrap;
  }

  .stock-badge {
    margin-left: 0;
  }
}
</style>
