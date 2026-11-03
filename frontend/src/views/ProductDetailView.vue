<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import { cartApi } from '../api/cart'
import { productApi } from '../api/product'
import { formatMoney } from '../utils/format'
import { createIdempotencyKey } from '../utils/idempotency'

const route = useRoute()
const product = ref(null)
const loading = ref(true)
const error = ref('')
const selectedSkuId = ref(null)
const quantity = ref(1)
const adding = ref(false)
const notice = ref('')

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

async function loadProduct() {
  loading.value = true
  error.value = ''
  try {
    product.value = await productApi.detail(route.params.id)
    selectedSkuId.value = activeSkus.value[0]?.id ?? null
  } catch (err) {
    error.value = err.message || '商品加载失败'
  } finally {
    loading.value = false
  }
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

onMounted(loadProduct)
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
            <img v-if="displayImage" :src="displayImage" :alt="product.name" class="detail-image" />
            <div v-else class="detail-image placeholder">暂无图片</div>
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
          <h1>{{ product.name }}</h1>
          <p class="detail-subtitle">{{ product.subtitle }}</p>

          <div class="price-panel">
            <span class="price-label">当前价格</span>
            <strong class="detail-price">
              {{ selectedSku ? formatMoney(selectedSku.price) : priceRange || '价格待定' }}
            </strong>
            <span v-if="selectedSku?.marketPrice" class="market-price">
              {{ formatMoney(selectedSku.marketPrice) }}
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
          <div v-if="notice" class="success-banner">
            {{ notice }}
            <RouterLink to="/cart">去购物车结算 →</RouterLink>
          </div>

          <button
            class="btn btn-primary btn-lg btn-block"
            type="button"
            :disabled="!selectedSku || adding"
            @click="addToCart"
          >
            {{ adding ? '加入中...' : '加入购物车' }}
          </button>

          <div class="detail-description">
            <h2>商品详情</h2>
            <p>{{ product.description || '暂无商品描述。' }}</p>
          </div>
        </section>
      </div>
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

.detail-info h1 {
  margin: 10px 0 8px;
  font-size: clamp(26px, 3.4vw, 38px);
  letter-spacing: -0.035em;
  color: var(--text-strong);
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

@media (max-width: 860px) {
  .detail-layout {
    grid-template-columns: 1fr;
    gap: 26px;
  }

  .detail-media {
    position: static;
  }
}
</style>
