<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import { cartApi } from '../api/cart'
import { formatMoney } from '../utils/format'

const router = useRouter()
const cart = ref({ items: [], summary: {} })
const loading = ref(true)
const error = ref('')
const busySku = ref(null)
const selectAllBusy = ref(false)

const items = computed(() => cart.value.items || [])
const availableItems = computed(() => items.value.filter((item) => item.available))
const selectedAvailableItems = computed(() => items.value.filter((item) => item.selected && item.available))
const selectedUnavailable = computed(() => items.value.some((item) => item.selected && !item.available))
const allSelected = computed(
  () => availableItems.value.length > 0 && availableItems.value.every((item) => item.selected)
)
const canCheckout = computed(
  () => selectedAvailableItems.value.length > 0 && !selectedUnavailable.value
)

async function loadCart() {
  loading.value = true
  error.value = ''
  try {
    cart.value = await cartApi.get()
  } catch (err) {
    error.value = err.message || '购物车加载失败'
  } finally {
    loading.value = false
  }
}

async function runItemAction(skuId, action) {
  busySku.value = skuId
  error.value = ''
  try {
    cart.value = await action()
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    busySku.value = null
  }
}

function changeQuantity(item, delta) {
  const next = Math.min(99, Math.max(1, (item.quantity || 1) + delta))
  if (next === item.quantity) return
  runItemAction(item.skuId, () => cartApi.updateQuantity(item.skuId, next))
}

function onQuantityInput(item, event) {
  const next = Number(event.target.value)
  const safe = Number.isFinite(next) ? Math.min(99, Math.max(1, Math.trunc(next))) : item.quantity
  event.target.value = safe
  if (safe === item.quantity) return
  runItemAction(item.skuId, () => cartApi.updateQuantity(item.skuId, safe))
}

function toggleItem(item) {
  if (!item.available) return
  runItemAction(item.skuId, () => cartApi.setSelected(item.skuId, !item.selected))
}

async function removeItem(item) {
  if (!window.confirm(`确认删除「${item.spuName || item.skuCode}」吗？`)) return
  await runItemAction(item.skuId, () => cartApi.remove(item.skuId))
}

async function toggleAll() {
  selectAllBusy.value = true
  error.value = ''
  try {
    if (allSelected.value) {
      cart.value = await cartApi.selectAll(false)
    } else {
      const targets = availableItems.value.filter((item) => !item.selected)
      await Promise.all(targets.map((item) => cartApi.setSelected(item.skuId, true)))
      cart.value = await cartApi.get()
    }
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    selectAllBusy.value = false
  }
}

function goCheckout() {
  if (!canCheckout.value) return
  router.push('/checkout')
}

onMounted(loadCart)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">Shopping Cart</span>
        <h1>我的购物车</h1>
      </div>
      <RouterLink to="/products" class="btn btn-ghost">继续购物</RouterLink>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadCart" />
    <LoadingState v-if="loading" label="正在加载购物车..." />

    <template v-else>
      <EmptyState
        v-if="!items.length"
        title="购物车还是空的"
        description="去商品列表挑选一些喜欢的商品吧。"
      >
        <RouterLink class="btn btn-primary" to="/products">去逛逛</RouterLink>
      </EmptyState>

      <div v-else class="cart-layout">
        <section class="cart-list surface-card">
          <div class="cart-toolbar">
            <label class="check-label">
              <input
                type="checkbox"
                :checked="allSelected"
                :disabled="selectAllBusy || !availableItems.length"
                @change="toggleAll"
              />
              <span>全选</span>
            </label>
            <span class="text-muted">共 {{ cart.summary.itemCount }} 种商品</span>
          </div>

          <article v-for="item in items" :key="item.skuId" class="cart-item" :class="{ muted: !item.available }">
            <input
              class="item-check"
              type="checkbox"
              :checked="item.selected"
              :disabled="!item.available || busySku === item.skuId"
              @change="toggleItem(item)"
            />

            <div class="cart-thumb">
              <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.spuName" />
              <div v-else class="cart-thumb placeholder">暂无图片</div>
            </div>

            <div class="cart-item-main">
              <RouterLink v-if="item.spuId" :to="`/products/${item.spuId}`" class="cart-name">
                {{ item.spuName || item.skuCode }}
              </RouterLink>
              <span v-else class="cart-name">{{ item.spuName || item.skuCode }}</span>
              <p class="cart-spec">{{ item.specText || item.skuCode }}</p>
              <p v-if="!item.available" class="unavailable">{{ item.unavailableReason || '商品不可购买' }}</p>
            </div>

            <div class="cart-unit-price">{{ formatMoney(item.price) }}</div>

            <div class="quantity-stepper small">
              <button type="button" :disabled="!item.available || item.quantity <= 1 || busySku === item.skuId" @click="changeQuantity(item, -1)">−</button>
              <input
                :value="item.quantity"
                type="number"
                min="1"
                max="99"
                :disabled="!item.available || busySku === item.skuId"
                @change="onQuantityInput(item, $event)"
              />
              <button type="button" :disabled="!item.available || item.quantity >= 99 || busySku === item.skuId" @click="changeQuantity(item, 1)">+</button>
            </div>

            <div class="cart-line-amount">{{ formatMoney(item.amount) }}</div>

            <button type="button" class="link-danger" :disabled="busySku === item.skuId" @click="removeItem(item)">
              删除
            </button>
          </article>
        </section>

        <aside class="cart-summary surface-card">
          <h2>结算信息</h2>
          <div class="summary-row">
            <span>已选数量</span>
            <strong>{{ cart.summary.selectedQuantity || 0 }}</strong>
          </div>
          <div class="summary-row">
            <span>已选金额</span>
            <strong>{{ formatMoney(cart.summary.selectedAmount) }}</strong>
          </div>
          <div class="summary-row muted-row">
            <span>可购买商品总额</span>
            <span>{{ formatMoney(cart.summary.totalAmount) }}</span>
          </div>
          <p v-if="selectedUnavailable" class="hint-warning">
            已选中不可购买商品，请取消勾选后再结算。
          </p>
          <p v-else-if="!selectedAvailableItems.length" class="hint-muted">
            请勾选需要结算的商品。
          </p>
          <button class="btn btn-primary btn-lg btn-block" type="button" :disabled="!canCheckout" @click="goCheckout">
            去结算
          </button>
          <p class="summary-note">金额由后端根据商品当前价格计算。</p>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 22px;
  align-items: start;
}

.cart-list {
  padding: 0;
  overflow: hidden;
}

.cart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-muted);
}

.check-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-strong);
}

.cart-item {
  display: grid;
  grid-template-columns: 24px 86px minmax(0, 1fr) 100px 120px 100px 50px;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
}

.cart-item:last-child {
  border-bottom: 0;
}

.cart-item.muted {
  opacity: 0.68;
}

.cart-thumb {
  width: 86px;
  height: 86px;
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface-muted);
}

.cart-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cart-thumb.placeholder {
  display: grid;
  place-items: center;
  font-size: 11px;
  color: var(--text-soft);
}

.cart-name {
  display: block;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.4;
}

.cart-spec {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.unavailable {
  margin: 6px 0 0;
  color: var(--danger);
  font-size: 12px;
}

.cart-unit-price,
.cart-line-amount {
  font-size: 14px;
  color: var(--text);
}

.cart-line-amount {
  color: var(--price);
  font-weight: 800;
}

.cart-summary {
  position: sticky;
  top: 88px;
  padding: 22px;
}

.cart-summary h2 {
  margin: 0 0 18px;
  font-size: 18px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--text-muted);
}

.summary-row strong {
  color: var(--text-strong);
  font-size: 16px;
}

.summary-row strong:last-child {
  color: var(--price);
  font-size: 22px;
}

.muted-row {
  padding-top: 12px;
  border-top: 1px dashed var(--border);
  font-size: 13px;
}

.hint-warning {
  color: var(--danger);
  font-size: 13px;
  line-height: 1.5;
}

.hint-muted,
.summary-note {
  color: var(--text-soft);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .cart-summary {
    position: static;
  }

  .cart-item {
    grid-template-columns: 24px 72px minmax(0, 1fr) 96px;
  }

  .cart-unit-price {
    display: none;
  }

  .cart-line-amount {
    grid-column: 3;
  }

  .cart-item .link-danger {
    grid-column: 4;
    text-align: right;
  }
}

@media (max-width: 560px) {
  .cart-item {
    grid-template-columns: 22px 64px minmax(0, 1fr);
    gap: 10px;
  }

  .cart-thumb {
    width: 64px;
    height: 64px;
  }

  .quantity-stepper,
  .cart-line-amount,
  .cart-item .link-danger {
    grid-column: 3;
    justify-self: start;
  }
}
</style>
