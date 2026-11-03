<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import { addressApi } from '../api/address'
import { cartApi } from '../api/cart'
import { orderApi } from '../api/order'
import { formatMoney } from '../utils/format'
import { createIdempotencyKey } from '../utils/idempotency'

const router = useRouter()
const idempotencyKey = createIdempotencyKey('order')

const cart = ref({ items: [], summary: {} })
const addresses = ref([])
const selectedAddressId = ref(null)
const loading = ref(true)
const error = ref('')
const submitError = ref('')
const submitting = ref(false)

const selectedItems = computed(() => (cart.value.items || []).filter((item) => item.selected && item.available))
const hasSelectedUnavailable = computed(() =>
  (cart.value.items || []).some((item) => item.selected && !item.available)
)
const selectedAddress = computed(() =>
  addresses.value.find((address) => address.id === selectedAddressId.value) || null
)
const canSubmit = computed(
  () => selectedItems.value.length > 0 && !hasSelectedUnavailable.value && selectedAddress.value && !submitting.value
)

function fullAddress(address) {
  return `${address.province}${address.city}${address.district}${address.detailAddress}`
}

async function loadCheckout() {
  loading.value = true
  error.value = ''
  try {
    const [cartData, addressData] = await Promise.all([cartApi.get(), addressApi.list()])
    cart.value = cartData
    addresses.value = addressData || []
    const preferred = addresses.value.find((address) => address.isDefault) || addresses.value[0]
    selectedAddressId.value = preferred?.id ?? null
  } catch (err) {
    error.value = err.message || '结算信息加载失败'
  } finally {
    loading.value = false
  }
}

async function submitOrder() {
  submitError.value = ''
  if (!selectedAddress.value) {
    submitError.value = '请选择收货地址'
    return
  }
  if (!selectedItems.value.length) {
    submitError.value = '没有可结算的商品'
    return
  }
  if (hasSelectedUnavailable.value) {
    submitError.value = '已选中不可购买商品，请返回购物车取消勾选'
    return
  }
  if (submitting.value) return

  submitting.value = true
  try {
    const order = await orderApi.create(selectedAddressId.value, idempotencyKey)
    router.replace(`/orders/${order.orderNo}`)
  } catch (err) {
    submitError.value = err.message || '下单失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadCheckout)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">Checkout</span>
        <h1>提交订单</h1>
      </div>
      <RouterLink to="/cart" class="btn btn-ghost">返回购物车</RouterLink>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadCheckout" />
    <LoadingState v-if="loading" label="正在准备订单..." />

    <template v-else>
      <EmptyState
        v-if="!selectedItems.length"
        title="没有可结算的商品"
        description="请返回购物车勾选需要购买的商品。"
      >
        <RouterLink class="btn btn-primary" to="/cart">返回购物车</RouterLink>
      </EmptyState>

      <div v-else class="checkout-layout">
        <div class="checkout-main">
          <section class="surface-card section-card">
            <div class="section-title-row">
              <h2>收货地址</h2>
              <RouterLink to="/addresses" class="text-link">管理地址</RouterLink>
            </div>

            <p v-if="!addresses.length" class="hint-warning">
              还没有收货地址，请先添加地址。
              <RouterLink to="/addresses" class="text-link">去添加</RouterLink>
            </p>

            <div v-else class="address-grid">
              <label
                v-for="address in addresses"
                :key="address.id"
                class="address-option"
                :class="{ active: selectedAddressId === address.id }"
              >
                <input v-model="selectedAddressId" type="radio" :value="address.id" />
                <div class="address-option-body">
                  <div class="address-option-head">
                    <strong>{{ address.receiverName }}</strong>
                    <span>{{ address.receiverPhone }}</span>
                    <span v-if="address.isDefault" class="tag">默认</span>
                  </div>
                  <p>{{ fullAddress(address) }}</p>
                </div>
              </label>
            </div>
          </section>

          <section class="surface-card section-card">
            <div class="section-title-row">
              <h2>商品清单</h2>
              <span class="text-muted">共 {{ selectedItems.length }} 种</span>
            </div>
            <div v-for="item in selectedItems" :key="item.skuId" class="checkout-item">
              <div class="checkout-thumb">
                <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.spuName" />
                <div v-else class="placeholder">暂无图片</div>
              </div>
              <div class="checkout-item-main">
                <strong>{{ item.spuName || item.skuCode }}</strong>
                <span>{{ item.specText || item.skuCode }}</span>
              </div>
              <span>{{ formatMoney(item.price) }} × {{ item.quantity }}</span>
              <strong class="line-amount">{{ formatMoney(item.amount) }}</strong>
            </div>
          </section>
        </div>

        <aside class="surface-card checkout-summary">
          <h2>订单汇总</h2>
          <div class="summary-row">
            <span>商品金额</span>
            <strong>{{ formatMoney(cart.summary.selectedAmount) }}</strong>
          </div>
          <div class="summary-row">
            <span>运费</span>
            <span>¥0.00</span>
          </div>
          <div class="summary-total">
            <span>应付金额</span>
            <strong>{{ formatMoney(cart.summary.selectedAmount) }}</strong>
          </div>
          <p class="summary-note">
            金额由后端重新读取商品价格并计算，提交后以订单金额为准。
          </p>
          <p v-if="hasSelectedUnavailable" class="hint-warning">
            已选中不可购买商品，请返回购物车取消勾选后再提交。
          </p>

          <ErrorBanner :message="submitError" />
          <button class="btn btn-primary btn-lg btn-block" type="button" :disabled="!canSubmit" @click="submitOrder">
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
          <p class="summary-note">提交成功后可在订单详情页使用模拟支付。</p>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.checkout-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 22px;
  align-items: start;
}

.checkout-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-card {
  padding: 22px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title-row h2 {
  margin: 0;
  font-size: 18px;
}

.address-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.address-option {
  display: flex;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.address-option.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.address-option input {
  margin-top: 3px;
}

.address-option-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-strong);
}

.address-option p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.checkout-item {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto auto;
  gap: 14px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-muted);
}

.checkout-thumb {
  width: 58px;
  height: 58px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface-muted);
}

.checkout-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.checkout-thumb .placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 10px;
  color: var(--text-soft);
}

.checkout-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.checkout-item-main strong {
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.checkout-item-main span {
  color: var(--text-muted);
  font-size: 12px;
}

.line-amount {
  color: var(--price);
}

.checkout-summary {
  position: sticky;
  top: 88px;
  padding: 22px;
}

.checkout-summary h2 {
  margin: 0 0 18px;
  font-size: 18px;
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding-top: 14px;
  margin-top: 8px;
  border-top: 1px dashed var(--border);
}

.summary-total strong {
  color: var(--price);
  font-size: 26px;
}

.hint-warning {
  color: var(--danger);
  font-size: 13px;
}

@media (max-width: 900px) {
  .checkout-layout {
    grid-template-columns: 1fr;
  }

  .checkout-summary {
    position: static;
  }
}

@media (max-width: 600px) {
  .address-grid {
    grid-template-columns: 1fr;
  }

  .checkout-item {
    grid-template-columns: 52px minmax(0, 1fr) auto;
  }

  .checkout-item > span:nth-child(3) {
    display: none;
  }
}
</style>
