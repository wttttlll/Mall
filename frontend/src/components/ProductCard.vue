<script setup>
import { formatPriceRange } from '../utils/format'

defineProps({
  product: { type: Object, required: true }
})
</script>

<template>
  <RouterLink :to="`/products/${product.id}`" class="product-card">
    <div class="product-image-wrap">
      <img
        v-if="product.mainImage"
        :src="product.mainImage"
        :alt="product.name"
        class="product-image"
        loading="lazy"
      />
      <div v-else class="product-image placeholder">暂无图片</div>
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
</template>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  color: inherit;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.product-card:hover {
  transform: translateY(-3px);
  border-color: #c9d8f8;
  box-shadow: var(--shadow-md);
}

.product-image-wrap {
  aspect-ratio: 4 / 3;
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
  font-size: 13px;
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
  color: var(--price);
  font-weight: 800;
  font-size: 17px;
}
</style>
