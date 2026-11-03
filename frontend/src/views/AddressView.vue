<script setup>
import { onMounted, reactive, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingState from '../components/LoadingState.vue'
import { addressApi } from '../api/address'

const addresses = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const saving = ref(false)
const actingId = ref(null)
const showForm = ref(false)
const editingId = ref(null)

const form = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  postalCode: '',
  isDefault: false
})

function resetForm() {
  Object.assign(form, {
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    postalCode: '',
    isDefault: false
  })
  editingId.value = null
}

async function loadAddresses() {
  loading.value = true
  error.value = ''
  try {
    addresses.value = await addressApi.list()
  } catch (err) {
    error.value = err.message || '地址加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  showForm.value = true
  message.value = ''
}

function openEdit(address) {
  editingId.value = address.id
  Object.assign(form, {
    receiverName: address.receiverName,
    receiverPhone: address.receiverPhone,
    province: address.province,
    city: address.city,
    district: address.district,
    detailAddress: address.detailAddress,
    postalCode: address.postalCode || '',
    isDefault: Boolean(address.isDefault)
  })
  showForm.value = true
  message.value = ''
}

function closeForm() {
  showForm.value = false
  resetForm()
}

function validate() {
  if (!form.receiverName.trim()) return '请填写收货人'
  if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) return '请填写正确的手机号'
  if (!form.province.trim() || !form.city.trim() || !form.district.trim()) return '请填写完整省市区'
  if (!form.detailAddress.trim()) return '请填写详细地址'
  return ''
}

async function save() {
  error.value = validate()
  if (error.value) return
  saving.value = true
  message.value = ''
  const payload = {
    receiverName: form.receiverName.trim(),
    receiverPhone: form.receiverPhone.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    detailAddress: form.detailAddress.trim(),
    postalCode: form.postalCode.trim() || undefined,
    isDefault: form.isDefault
  }
  try {
    if (editingId.value) {
      await addressApi.update(editingId.value, payload)
      message.value = '地址已更新'
    } else {
      await addressApi.create(payload)
      message.value = '地址已添加'
    }
    closeForm()
    await loadAddresses()
  } catch (err) {
    error.value = err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function removeAddress(address) {
  if (!window.confirm(`确认删除「${address.receiverName}」的地址吗？`)) return
  actingId.value = address.id
  error.value = ''
  try {
    await addressApi.remove(address.id)
    message.value = '地址已删除'
    await loadAddresses()
  } catch (err) {
    error.value = err.message || '删除失败'
  } finally {
    actingId.value = null
  }
}

async function makeDefault(address) {
  actingId.value = address.id
  error.value = ''
  try {
    await addressApi.setDefault(address.id)
    message.value = '已设为默认地址'
    await loadAddresses()
  } catch (err) {
    error.value = err.message || '设置失败'
  } finally {
    actingId.value = null
  }
}

function fullAddress(address) {
  return `${address.province}${address.city}${address.district}${address.detailAddress}`
}

onMounted(loadAddresses)
</script>

<template>
  <div class="container page-section">
    <div class="page-heading">
      <div>
        <span class="eyebrow">Addresses</span>
        <h1>收货地址</h1>
      </div>
      <button class="btn btn-primary" type="button" @click="openCreate">新增地址</button>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadAddresses" />
    <div v-if="message" class="success-banner">{{ message }}</div>

    <section v-if="showForm" class="surface-card address-form-card">
      <div class="section-title-row">
        <h2>{{ editingId ? '编辑地址' : '新增地址' }}</h2>
        <button type="button" class="btn btn-ghost btn-sm" @click="closeForm">关闭</button>
      </div>
      <form class="form-stack" @submit.prevent="save">
        <div class="form-grid">
          <label class="field">
            <span>收货人 *</span>
            <input v-model="form.receiverName" type="text" maxlength="64" placeholder="姓名" />
          </label>
          <label class="field">
            <span>手机号 *</span>
            <input v-model="form.receiverPhone" type="tel" maxlength="11" placeholder="11 位手机号" />
          </label>
        </div>
        <div class="form-grid three">
          <label class="field">
            <span>省份 *</span>
            <input v-model="form.province" type="text" maxlength="64" placeholder="如：广东省" />
          </label>
          <label class="field">
            <span>城市 *</span>
            <input v-model="form.city" type="text" maxlength="64" placeholder="如：深圳市" />
          </label>
          <label class="field">
            <span>区县 *</span>
            <input v-model="form.district" type="text" maxlength="64" placeholder="如：南山区" />
          </label>
        </div>
        <label class="field">
          <span>详细地址 *</span>
          <input v-model="form.detailAddress" type="text" maxlength="255" placeholder="街道、门牌号等" />
        </label>
        <div class="form-grid">
          <label class="field">
            <span>邮编</span>
            <input v-model="form.postalCode" type="text" maxlength="16" placeholder="选填" />
          </label>
          <label class="check-label field-check">
            <input v-model="form.isDefault" type="checkbox" />
            <span>设为默认地址</span>
          </label>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" type="submit" :disabled="saving">
            {{ saving ? '保存中...' : '保存地址' }}
          </button>
          <button class="btn btn-ghost" type="button" @click="closeForm">取消</button>
        </div>
      </form>
    </section>

    <LoadingState v-if="loading" label="正在加载地址..." />

    <template v-else>
      <EmptyState v-if="!addresses.length && !showForm" title="还没有收货地址" description="添加地址后即可提交订单。">
        <button class="btn btn-primary" type="button" @click="openCreate">新增地址</button>
      </EmptyState>

      <div v-else class="address-list">
        <article v-for="address in addresses" :key="address.id" class="address-card surface-card">
          <div class="address-head">
            <div>
              <strong>{{ address.receiverName }}</strong>
              <span>{{ address.receiverPhone }}</span>
            </div>
            <span v-if="address.isDefault" class="tag">默认地址</span>
          </div>
          <p>{{ fullAddress(address) }}</p>
          <p v-if="address.postalCode" class="text-muted">邮编：{{ address.postalCode }}</p>
          <div class="address-actions">
            <button class="text-link" type="button" @click="openEdit(address)">编辑</button>
            <button
              v-if="!address.isDefault"
              class="text-link"
              type="button"
              :disabled="actingId === address.id"
              @click="makeDefault(address)"
            >
              设为默认
            </button>
            <button
              class="link-danger"
              type="button"
              :disabled="actingId === address.id"
              @click="removeAddress(address)"
            >
              删除
            </button>
          </div>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.address-form-card {
  padding: 22px;
  margin-bottom: 20px;
}

.address-form-card h2 {
  margin: 0;
  font-size: 18px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.address-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.address-card {
  padding: 20px;
}

.address-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.address-head strong {
  font-size: 16px;
  margin-right: 10px;
}

.address-head span {
  color: var(--text-muted);
  font-size: 13px;
}

.address-card p {
  margin: 10px 0 0;
  color: var(--text-muted);
  line-height: 1.6;
}

.address-actions {
  display: flex;
  gap: 14px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.form-actions {
  display: flex;
  gap: 10px;
}

.field-check {
  align-self: end;
  padding-bottom: 10px;
}

@media (max-width: 760px) {
  .address-list {
    grid-template-columns: 1fr;
  }

  .form-grid.three {
    grid-template-columns: 1fr;
  }
}
</style>
