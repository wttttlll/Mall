<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import FavoriteButton from '../components/FavoriteButton.vue'
import LoadingState from '../components/LoadingState.vue'
import { favoriteApi } from '../api/favorite'
import { formatDateTime } from '../utils/format'

const favorites = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const busySpuId = ref(null)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const totalPages = computed(() => Math.max(1, Math.ceil(page.total / page.pageSize)))

async function loadFavorites() {
  loading.value = true
  error.value = ''
  try {
    const data = await favoriteApi.list({ pageNum: page.pageNum, pageSize: page.pageSize })
    favorites.value = data.list || []
    page.total = data.total || 0
  } catch (err) {
    error.value = err.message || '收藏加载失败'
  } finally {
    loading.value = false
  }
}

function goPage(target) {
  if (target < 1 || target > totalPages.value || target === page.pageNum) return
  page.pageNum = target
  loadFavorites()
}

async function removeFavorite(item) {
  if (busySpuId.value) return
  busySpuId.value = item.spuId
  error.value = ''
  message.value = ''
  try {
    await favoriteApi.remove(item.spuId)
    message.value = `已取消收藏「${item.spuName || '该商品'}」`
    if (favorites.value.length === 1 && page.pageNum > 1) page.pageNum -= 1
    await loadFavorites()
  } catch (err) {
    error.value = err.message || '取消收藏失败'
  } finally {
    busySpuId.value = null
  }
}

onMounted(loadFavorites)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">Wishlist</span>
        <h1>我的收藏</h1>
      </div>
      <RouterLink to="/products" class="btn btn-ghost">去逛逛</RouterLink>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadFavorites" />
    <div v-if="message" class="success-banner">{{ message }}</div>

    <LoadingState v-if="loading" label="正在加载收藏..." />

    <template v-else>
      <EmptyState v-if="!favorites.length" title="还没有收藏商品" description="在商品列表或详情页点击心形按钮即可收藏。">
        <RouterLink class="btn btn-primary" to="/products">去发现商品</RouterLink>
      </EmptyState>

      <div v-else class="favorite-grid">
        <article v-for="item in favorites" :key="item.spuId" class="surface-card favorite-card">
          <RouterLink
            v-if="item.available"
            :to="`/products/${item.spuId}`"
            class="favorite-image-wrap"
          >
            <img v-if="item.mainImage" :src="item.mainImage" :alt="item.spuName" class="favorite-image" />
            <div v-else class="favorite-image placeholder">暂无图片</div>
          </RouterLink>
          <div v-else class="favorite-image-wrap unavailable">
            <img v-if="item.mainImage" :src="item.mainImage" :alt="item.spuName" class="favorite-image" />
            <div v-else class="favorite-image placeholder">暂无图片</div>
            <span class="unavailable-mask">{{ item.unavailableReason || '商品不可用' }}</span>
          </div>

          <FavoriteButton
            class="favorite-card-button"
            compact
            active
            active-label="取消收藏"
            :busy="busySpuId === item.spuId"
            @toggle="removeFavorite(item)"
          />

          <div class="favorite-body">
            <RouterLink v-if="item.available" :to="`/products/${item.spuId}`" class="favorite-name">
              {{ item.spuName }}
            </RouterLink>
            <strong v-else class="favorite-name unavailable-name">{{ item.spuName || '商品已删除' }}</strong>
            <p class="favorite-subtitle">{{ item.subtitle || '收藏于 ' + formatDateTime(item.createdAt) }}</p>
            <div class="favorite-foot">
              <span class="table-muted">{{ formatDateTime(item.createdAt) }}</span>
              <RouterLink v-if="item.available" class="btn btn-ghost btn-sm" :to="`/products/${item.spuId}`">
                查看商品
              </RouterLink>
              <span v-else class="favorite-unavailable">{{ item.unavailableReason || '商品不可用' }}</span>
            </div>
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
.favorite-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.favorite-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.favorite-image-wrap {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--surface-muted);
  overflow: hidden;
}

.favorite-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.favorite-image.placeholder {
  display: grid;
  place-items: center;
  color: var(--text-soft);
  font-size: 12px;
}

.favorite-image-wrap.unavailable .favorite-image {
  filter: grayscale(0.75);
  opacity: 0.55;
}

.unavailable-mask {
  position: absolute;
  inset: auto 10px 10px;
  padding: 5px 8px;
  border-radius: 7px;
  background: rgba(17, 24, 39, 0.78);
  color: #fff;
  font-size: 11px;
  text-align: center;
}

.favorite-card-button {
  position: absolute;
  top: 9px;
  right: 9px;
  z-index: 2;
}

.favorite-body {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.favorite-name {
  color: var(--text-strong);
  font-weight: 800;
  font-size: 15px;
  line-height: 1.4;
}

.unavailable-name {
  color: var(--text-muted);
}

.favorite-subtitle {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  min-height: 18px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.favorite-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  padding-top: 8px;
}

.favorite-unavailable {
  color: #b45309;
  font-size: 11px;
  font-weight: 700;
}

.table-muted {
  color: var(--text-soft);
  font-size: 11px;
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

@media (max-width: 980px) {
  .favorite-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .favorite-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .favorite-grid {
    grid-template-columns: 1fr;
  }
}
</style>
