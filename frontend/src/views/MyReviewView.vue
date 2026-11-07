<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import StarRating from '../components/StarRating.vue'
import { reviewApi } from '../api/review'
import { formatDateTime } from '../utils/format'

const reviews = ref([])
const loading = ref(true)
const error = ref('')
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))

async function loadReviews() {
  loading.value = true
  error.value = ''
  try {
    const data = await reviewApi.mine({ pageNum: page.pageNum, pageSize: page.pageSize })
    reviews.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '评价加载失败'
  } finally {
    loading.value = false
  }
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadReviews()
}

onMounted(loadReviews)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">My Reviews</span>
        <h1>我的评价</h1>
      </div>
      <RouterLink to="/orders" class="btn btn-ghost">查看订单</RouterLink>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadReviews" />
    <LoadingState v-if="loading" label="正在加载评价..." />

    <template v-else>
      <EmptyState v-if="!reviews.length" title="还没有评价" description="完成订单后，可以在订单详情中评价购买过的商品。">
        <RouterLink class="btn btn-primary" to="/orders">查看我的订单</RouterLink>
      </EmptyState>

      <div v-else class="my-review-list">
        <article v-for="review in reviews" :key="review.id" class="surface-card my-review-card">
          <RouterLink :to="`/products/${review.spuId}`" class="my-review-product">
            <div class="review-image-wrap">
              <img v-if="review.imageUrl" :src="review.imageUrl" :alt="review.spuName" class="review-image" />
              <div v-else class="review-image placeholder">暂无图片</div>
            </div>
            <div class="review-product-main">
              <strong>{{ review.spuName || review.skuCode || `商品 ${review.spuId}` }}</strong>
              <span>SKU {{ review.skuCode || review.skuId }} · 订单 {{ review.orderNo }}</span>
            </div>
          </RouterLink>

          <div class="my-review-body">
            <div class="my-review-head">
              <StarRating :model-value="review.rating" readonly size="sm" show-value />
              <span class="review-time">{{ formatDateTime(review.createdAt) }}</span>
            </div>
            <p>{{ review.content || '用户未填写文字评价' }}</p>
          </div>
        </article>
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
.my-review-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.my-review-card {
  padding: 18px;
}

.my-review-product {
  display: flex;
  align-items: center;
  gap: 12px;
  color: inherit;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border);
}

.review-image-wrap {
  width: 58px;
  height: 58px;
  flex: none;
  overflow: hidden;
  border-radius: 10px;
  background: var(--surface-muted);
}

.review-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.review-image.placeholder {
  display: grid;
  place-items: center;
  color: var(--text-soft);
  font-size: 10px;
}

.review-product-main {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.review-product-main strong {
  color: var(--text-strong);
  font-size: 15px;
}

.review-product-main span {
  color: var(--text-muted);
  font-size: 12px;
  word-break: break-all;
}

.my-review-body {
  padding-top: 13px;
}

.my-review-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.review-time {
  color: var(--text-soft);
  font-size: 12px;
}

.my-review-body p {
  margin: 9px 0 0;
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-line;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 24px;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
