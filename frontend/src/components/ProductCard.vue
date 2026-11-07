<script setup>
import { ref, watch } from 'vue'
import FavoriteButton from './FavoriteButton.vue'
import { formatPriceRange } from '../utils/format'

const props = defineProps({
  product: { type: Object, required: true },
  favorite: { type: Boolean, default: false },
  favoriteBusy: { type: Boolean, default: false }
})
defineEmits(['toggle-favorite'])

const imageFailed = ref(false)
watch(
  () => props.product?.mainImage,
  () => {
    imageFailed.value = false
  }
)
</script>

<template>
  <article class="product-card">
    <RouterLink :to="`/products/${product.id}`" class="product-link">
      <div class="product-image-wrap">
        <img
          v-if="product.mainImage && !imageFailed"
          :src="product.mainImage"
          :alt="product.name"
          class="product-image"
          @error="imageFailed = true"
        />
        <div v-else class="product-image placeholder">
          <span>{{ imageFailed ? '图片暂不可用' : '暂无图片' }}</span>
        </div>
      </div>
      <div class="product-body">
        <div class="product-meta">
          <span v-if="product.categoryName" class="tag">{{ product.categoryName }}</span>
          <span v-if="product.brandName" class="tag tag-muted">{{ product.brandName }}</span>
        </div>
        <h3 class="product-name">{{ product.name }}</h3>
        <p class="product-subtitle">{{ product.subtitle || '精选好物，品质保障' }}</p>
        <div class="product-price">{{ formatPriceRange(product.minPrice, product.maxPrice) }}</div>
      </div>
    </RouterLink>
    <FavoriteButton
      class="card-favorite"
      compact
      :active="favorite"
      :busy="favoriteBusy"
      @toggle="$emit('toggle-favorite', product)"
    />
  </article>
</template>

<style scoped>
.product-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  color: inherit;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.product-link {
  display: flex;
  flex-direction: column;
  flex: 1;
  color: inherit;
}

.card-favorite {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
}

.product-card:hover {
  transform: translateY(-3px);
  border-color: #c9d8f8;
  box-shadow: var(--shadow-md);
}

.product-image-wrap {
  aspect-ratio: 1 / 1;
  background: var(--surface-muted);
  overflow: hidden;
}

.product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s ease;
}

.product-card:hover .product-image {
  transform: scale(1.03);
}

.product-image.placeholder {
  display: grid;
  place-items: center;
  color: var(--text-soft);
  font-size: 12px;
  background:
    linear-gradient(135deg, rgba(219, 234, 254, 0.42), transparent 60%),
    var(--surface-muted);
}

.product-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  flex: 1;
}

.product-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  display: inline-flex;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
}

.tag-muted {
  background: var(--surface-muted);
  color: var(--text-muted);
}

.product-name {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
  color: var(--text-strong);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-subtitle {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  min-height: 20px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-price {
  margin-top: auto;
  color: #b4233a;
  font-weight: 800;
  font-size: 18px;
  letter-spacing: -0.02em;
}
</style>
