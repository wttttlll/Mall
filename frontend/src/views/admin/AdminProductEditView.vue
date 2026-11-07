<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import { adminProductApi } from '../../api/adminProduct'
import { useAuthStore } from '../../stores/auth'
import { validateProductForm, validateSkuForm } from '../../utils/adminValidation'
import { formatDateTime, formatMoney, productStatusClass, productStatusText } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isNew = computed(() => !route.params.id)
const spuId = ref(route.params.id ? Number(route.params.id) : null)
const product = ref(null)
const categories = ref([])
const brands = ref([])
const loading = ref(!isNew.value)
const savingSpu = ref(false)
const skuSaving = ref(false)
const skuEditing = ref(false)
const error = ref('')
const message = ref('')

const form = reactive({
  name: '',
  subtitle: '',
  categoryId: '',
  brandId: '',
  mainImage: '',
  imagesText: '',
  description: '',
  sort: 0
})

const initialSku = reactive({
  skuCode: '',
  price: '',
  marketPrice: '',
  imageUrl: '',
  status: 1
})

const skuForm = reactive({
  id: null,
  skuCode: '',
  price: '',
  marketPrice: '',
  imageUrl: '',
  status: 1,
  selectedValueBySpec: {}
})

const newSpec = reactive({ name: '', sort: 0 })
const newValueText = reactive({})
const busySpec = ref(null)

const specs = computed(() => product.value?.specs || [])
const skus = computed(() => product.value?.skus || [])
const isOnShelf = computed(() => product.value?.status === 'ON_SHELF')
const canCreate = computed(() => auth.hasPermission('product:spu:create'))
const canUpdateSpu = computed(() => auth.hasPermission('product:spu:update'))
const canSkuCreate = computed(() => auth.hasPermission('product:sku:create'))
const canSkuUpdate = computed(() => auth.hasPermission('product:sku:update'))
const canSkuDelete = computed(() => auth.hasPermission('product:sku:delete'))
const canSpecCreate = computed(() => auth.hasPermission('product:spec:create'))
const canSpecUpdate = computed(() => auth.hasPermission('product:spec:update'))
const canSpecDelete = computed(() => auth.hasPermission('product:spec:delete'))
const editableSpu = computed(() => (isNew.value ? canCreate.value : canUpdateSpu.value && !isOnShelf.value))
const editableStructure = computed(() => isOnShelf.value === false)
const canAddSku = computed(() => canSkuCreate.value && editableStructure.value)
const canEditSku = computed(() => canSkuUpdate.value && editableStructure.value)
const canRemoveSku = computed(() => canSkuDelete.value && editableStructure.value)
const canEditSpecs = computed(
  () => canSpecCreate.value && product.value?.status === 'DRAFT'
)

const categoryOptions = computed(() => {
  const options = []
  const walk = (nodes, depth) => {
    for (const node of nodes || []) {
      options.push({ ...node, depth })
      walk(node.children, depth + 1)
    }
  }
  walk(categories.value, 0)
  return options
})

function splitImages() {
  return (form.imagesText || '')
    .split(/\r?\n|,/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function applyProduct(data) {
  product.value = data
  Object.assign(form, {
    name: data.name || '',
    subtitle: data.subtitle || '',
    categoryId: data.categoryId || '',
    brandId: data.brandId || '',
    mainImage: data.mainImage || '',
    imagesText: (data.images || []).join('\n'),
    description: data.description || '',
    sort: data.sort ?? 0
  })
}

async function loadReferences() {
  try {
    categories.value = await adminProductApi.categories()
  } catch {
    categories.value = []
  }
  try {
    brands.value = await adminProductApi.brands()
  } catch {
    brands.value = []
  }
}

async function loadProduct() {
  if (isNew.value) return
  loading.value = true
  error.value = ''
  try {
    applyProduct(await adminProductApi.spu(spuId.value))
  } catch (err) {
    error.value = err.message || '商品加载失败'
  } finally {
    loading.value = false
  }
}

function productPayload() {
  return {
    name: form.name.trim(),
    subtitle: form.subtitle.trim() || null,
    categoryId: Number(form.categoryId),
    brandId: form.brandId ? Number(form.brandId) : null,
    mainImage: form.mainImage.trim() || null,
    images: splitImages(),
    description: form.description.trim() || null,
    sort: Number(form.sort) || 0
  }
}

async function saveProduct() {
  error.value = validateProductForm(form)
  if (error.value) return
  savingSpu.value = true
  message.value = ''
  try {
    if (isNew.value) {
      if (!canCreate.value) throw new Error('当前账号没有创建商品权限')
      const skuError = validateSkuForm(initialSku, [])
      if (skuError) {
        error.value = `初始 SKU：${skuError}`
        return
      }
      const created = await adminProductApi.createSpu(productPayload())
      try {
        await adminProductApi.createSku(created.id, {
          skuCode: initialSku.skuCode.trim(),
          price: Number(initialSku.price),
          marketPrice: initialSku.marketPrice ? Number(initialSku.marketPrice) : null,
          imageUrl: initialSku.imageUrl.trim() || form.mainImage.trim() || null,
          specValueIds: [],
          status: Number(initialSku.status)
        })
        await router.replace({ path: `/admin/products/${created.id}`, query: { created: '1' } })
      } catch {
        await router.replace({ path: `/admin/products/${created.id}`, query: { created: 'sku-failed' } })
      }
      return
    }
    if (!editableSpu.value) throw new Error('商品已上架，请先下架后再编辑')
    applyProduct(await adminProductApi.updateSpu(spuId.value, productPayload()))
    message.value = '商品基础信息已保存'
  } catch (err) {
    error.value = err.message || '保存失败'
  } finally {
    savingSpu.value = false
  }
}

function resetSkuForm() {
  Object.assign(skuForm, {
    id: null,
    skuCode: '',
    price: '',
    marketPrice: '',
    imageUrl: '',
    status: 1,
    selectedValueBySpec: {}
  })
  skuEditing.value = false
}

function startAddSku() {
  resetSkuForm()
  skuEditing.value = true
}

function startEditSku(sku) {
  const selectedValueBySpec = {}
  for (const spec of specs.value) {
    const matched = (spec.values || []).find((value) =>
      (sku.specValues || []).some((selected) => selected.id === value.id)
    )
    if (matched) selectedValueBySpec[spec.id] = matched.id
  }
  Object.assign(skuForm, {
    id: sku.id,
    skuCode: sku.skuCode,
    price: sku.price,
    marketPrice: sku.marketPrice || '',
    imageUrl: sku.imageUrl || '',
    status: sku.status,
    selectedValueBySpec
  })
  skuEditing.value = true
}

async function saveSku() {
  const validation = validateSkuForm(skuForm, specs.value)
  if (validation) {
    error.value = `SKU：${validation}`
    return
  }
  skuSaving.value = true
  error.value = ''
  message.value = ''
  try {
    const specValueIds = specs.value
      .map((spec) => Number(skuForm.selectedValueBySpec[spec.id]))
      .filter(Boolean)
    const payload = {
      price: Number(skuForm.price),
      marketPrice: skuForm.marketPrice ? Number(skuForm.marketPrice) : null,
      imageUrl: skuForm.imageUrl.trim() || null,
      specValueIds,
      status: Number(skuForm.status)
    }
    if (skuForm.id) {
      if (!canEditSku.value) throw new Error('当前状态或权限不允许修改 SKU')
      await adminProductApi.updateSku(spuId.value, skuForm.id, payload)
      message.value = 'SKU 已更新'
    } else {
      if (!canAddSku.value) throw new Error('当前状态或权限不允许新增 SKU')
      await adminProductApi.createSku(spuId.value, {
        skuCode: skuForm.skuCode.trim(),
        ...payload
      })
      message.value = 'SKU 已添加'
    }
    resetSkuForm()
    await loadProduct()
  } catch (err) {
    error.value = err.message || 'SKU 保存失败'
  } finally {
    skuSaving.value = false
  }
}

async function removeSku(sku) {
  if (!window.confirm(`确认删除 SKU「${sku.skuCode}」吗？`)) return
  error.value = ''
  try {
    await adminProductApi.deleteSku(spuId.value, sku.id)
    message.value = 'SKU 已删除'
    await loadProduct()
  } catch (err) {
    error.value = err.message || '删除 SKU 失败'
  }
}

async function addSpec() {
  if (!newSpec.name.trim()) {
    error.value = '请填写规格名称'
    return
  }
  busySpec.value = 'new'
  try {
    await adminProductApi.createSpec(spuId.value, {
      name: newSpec.name.trim(),
      sort: Number(newSpec.sort) || 0
    })
    newSpec.name = ''
    newSpec.sort = 0
    message.value = '规格已添加'
    await loadProduct()
  } catch (err) {
    error.value = err.message || '添加规格失败'
  } finally {
    busySpec.value = null
  }
}

async function renameSpec(spec) {
  const name = window.prompt('修改规格名称', spec.name)
  if (!name || name.trim() === spec.name) return
  try {
    await adminProductApi.updateSpec(spuId.value, spec.id, { name: name.trim(), sort: spec.sort ?? 0 })
    await loadProduct()
  } catch (err) {
    error.value = err.message || '修改规格失败'
  }
}

async function removeSpec(spec) {
  if (!window.confirm(`确认删除规格「${spec.name}」及其规格值吗？`)) return
  try {
    await adminProductApi.deleteSpec(spuId.value, spec.id)
    await loadProduct()
  } catch (err) {
    error.value = err.message || '删除规格失败'
  }
}

async function addSpecValue(spec) {
  const value = (newValueText[spec.id] || '').trim()
  if (!value) {
    error.value = '请填写规格值'
    return
  }
  busySpec.value = spec.id
  try {
    await adminProductApi.createSpecValue(spuId.value, spec.id, { value, sort: 0 })
    newValueText[spec.id] = ''
    await loadProduct()
  } catch (err) {
    error.value = err.message || '添加规格值失败'
  } finally {
    busySpec.value = null
  }
}

async function renameSpecValue(spec, value) {
  const text = window.prompt('修改规格值', value.value)
  if (!text || text.trim() === value.value) return
  try {
    await adminProductApi.updateSpecValue(spuId.value, spec.id, value.id, {
      value: text.trim(),
      sort: value.sort ?? 0
    })
    await loadProduct()
  } catch (err) {
    error.value = err.message || '修改规格值失败'
  }
}

async function removeSpecValue(spec, value) {
  if (!window.confirm(`确认删除规格值「${value.value}」吗？`)) return
  try {
    await adminProductApi.deleteSpecValue(spuId.value, spec.id, value.id)
    await loadProduct()
  } catch (err) {
    error.value = err.message || '删除规格值失败'
  }
}

onMounted(async () => {
  await loadReferences()
  if (isNew.value) {
    if (!canCreate.value) error.value = '当前账号没有创建商品权限'
  } else {
    await loadProduct()
    if (route.query.created === '1') message.value = '商品已创建（草稿）'
    if (route.query.created === 'sku-failed') {
      error.value = '商品已创建，但初始 SKU 添加失败，请在下方补充 SKU'
    }
  }
})
</script>

<template>
  <div>
    <div class="admin-page-heading">
      <div>
        <RouterLink to="/admin/products" class="back-link">← 返回商品列表</RouterLink>
        <h1>{{ isNew ? '新增商品' : (product?.name || '商品详情') }}</h1>
        <p v-if="!isNew && product" class="admin-hint">
          ID {{ product.id }} · 创建于 {{ formatDateTime(product.createdAt) }}
        </p>
      </div>
      <span v-if="!isNew && product" class="status-badge" :class="productStatusClass(product.status)">
        {{ productStatusText(product.status) }}
      </span>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadProduct" />
    <div v-if="message" class="success-banner">{{ message }}</div>
    <div v-if="isOnShelf" class="admin-readonly-note">
      商品已上架：基础信息、规格和 SKU 不可修改。请先在商品列表下架后再编辑。
    </div>
    <div v-else-if="!isNew && !editableSpu" class="admin-readonly-note">
      当前账号没有修改商品的权限，以下内容为只读。
    </div>

    <LoadingState v-if="loading" label="正在加载商品详情..." />

    <template v-else>
      <section class="admin-section-card">
        <div class="admin-section-title">
          <h2>{{ isNew ? '基础信息与初始 SKU' : '基础信息' }}</h2>
          <button class="btn btn-primary" type="button" :disabled="savingSpu || !editableSpu" @click="saveProduct">
            {{ savingSpu ? '保存中...' : (isNew ? '创建商品' : '保存基础信息') }}
          </button>
        </div>

        <form class="form-stack" @submit.prevent="saveProduct">
          <div class="form-grid">
            <label class="field">
              <span>商品名称 *</span>
              <input v-model="form.name" type="text" maxlength="128" :disabled="!editableSpu" />
            </label>
            <label class="field">
              <span>副标题</span>
              <input v-model="form.subtitle" type="text" maxlength="255" :disabled="!editableSpu" />
            </label>
          </div>
          <div class="form-grid">
            <label class="field">
              <span>分类 *</span>
              <select v-model="form.categoryId" :disabled="!editableSpu">
                <option value="">请选择分类</option>
                <option v-for="item in categoryOptions" :key="item.id" :value="item.id">
                  {{ '　'.repeat(item.depth) }}{{ item.name }}
                </option>
              </select>
            </label>
            <label class="field">
              <span>品牌</span>
              <select v-model="form.brandId" :disabled="!editableSpu">
                <option value="">无品牌</option>
                <option v-for="brand in brands" :key="brand.id" :value="brand.id">{{ brand.name }}</option>
              </select>
            </label>
          </div>
          <label class="field">
            <span>主图 URL *</span>
            <input v-model="form.mainImage" type="url" maxlength="512" :disabled="!editableSpu" />
          </label>
          <label class="field">
            <span>商品图片 URL（每行一个，最多 20 张）</span>
            <textarea v-model="form.imagesText" rows="3" :disabled="!editableSpu"></textarea>
          </label>
          <label class="field">
            <span>商品描述</span>
            <textarea v-model="form.description" rows="4" maxlength="5000" :disabled="!editableSpu"></textarea>
          </label>
          <div class="form-grid">
            <label class="field">
              <span>排序</span>
              <input v-model="form.sort" type="number" :disabled="!editableSpu" />
            </label>
          </div>

          <template v-if="isNew">
            <div class="admin-section-title">
              <h2>初始 SKU</h2>
            </div>
            <div class="form-grid three">
              <label class="field">
                <span>SKU 编码 *</span>
                <input v-model="initialSku.skuCode" type="text" maxlength="64" />
              </label>
              <label class="field">
                <span>销售价 *</span>
                <input v-model="initialSku.price" type="number" min="0.01" step="0.01" />
              </label>
              <label class="field">
                <span>市场价</span>
                <input v-model="initialSku.marketPrice" type="number" min="0" step="0.01" />
              </label>
            </div>
            <div class="form-grid">
              <label class="field">
                <span>SKU 图片 URL</span>
                <input v-model="initialSku.imageUrl" type="url" maxlength="512" />
              </label>
              <label class="field">
                <span>SKU 状态</span>
                <select v-model="initialSku.status">
                  <option :value="1">启用</option>
                  <option :value="0">禁用</option>
                </select>
              </label>
            </div>
            <p class="admin-hint">创建后商品为草稿，可在编辑页补充规格、SKU 并上架。</p>
          </template>
        </form>
      </section>

      <template v-if="!isNew">
        <section class="admin-section-card">
          <div class="admin-section-title">
            <h2>规格管理</h2>
            <span class="admin-hint" v-if="!canEditSpecs">仅草稿状态可维护规格</span>
          </div>

          <div v-if="specs.length" class="spec-list">
            <article v-for="spec in specs" :key="spec.id" class="spec-card">
              <div class="spec-head">
                <strong>{{ spec.name }}</strong>
                <div class="table-actions">
                  <button v-if="canEditSpecs && canSpecUpdate" class="text-link" type="button" @click="renameSpec(spec)">改名</button>
                  <button v-if="canEditSpecs && canSpecDelete" class="link-danger" type="button" @click="removeSpec(spec)">删除</button>
                </div>
              </div>
              <div class="spec-values">
                <span v-for="value in spec.values" :key="value.id" class="spec-value">
                  {{ value.value }}
                  <button v-if="canEditSpecs && canSpecUpdate" type="button" @click="renameSpecValue(spec, value)">✎</button>
                  <button v-if="canEditSpecs && canSpecDelete" type="button" @click="removeSpecValue(spec, value)">×</button>
                </span>
                <span v-if="!spec.values?.length" class="table-muted">暂无规格值</span>
              </div>
              <div v-if="canEditSpecs && canSpecCreate" class="spec-add-value">
                <input v-model="newValueText[spec.id]" type="text" maxlength="64" placeholder="新增规格值" />
                <button class="btn btn-ghost btn-sm" type="button" :disabled="busySpec === spec.id" @click="addSpecValue(spec)">
                  添加
                </button>
              </div>
            </article>
          </div>
          <p v-else class="admin-hint">暂无规格。无规格商品可以直接维护 SKU；有规格商品需要先添加规格和规格值。</p>

          <form v-if="canEditSpecs && canSpecCreate" class="spec-add-form" @submit.prevent="addSpec">
            <input v-model="newSpec.name" type="text" maxlength="64" placeholder="新规格名称，如：颜色" />
            <input v-model="newSpec.sort" type="number" placeholder="排序" />
            <button class="btn btn-ghost btn-sm" type="submit" :disabled="busySpec === 'new'">新增规格</button>
          </form>
        </section>

        <section class="admin-section-card">
          <div class="admin-section-title">
            <h2>SKU 管理</h2>
            <button v-if="canAddSku" class="btn btn-primary btn-sm" type="button" @click="startAddSku">新增 SKU</button>
          </div>

          <div v-if="skus.length" class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>SKU 编码</th>
                  <th>规格</th>
                  <th>销售价</th>
                  <th>市场价</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="sku in skus" :key="sku.id">
                  <td><strong>{{ sku.skuCode }}</strong></td>
                  <td class="table-muted">
                    {{ (sku.specValues || []).map((item) => item.value).join(' / ') || '无规格' }}
                  </td>
                  <td>{{ formatMoney(sku.price) }}</td>
                  <td class="table-muted">{{ sku.marketPrice ? formatMoney(sku.marketPrice) : '-' }}</td>
                  <td>
                    <span class="status-badge" :class="sku.status === 1 ? 'status-on-shelf' : 'status-cancelled'">
                      {{ sku.status === 1 ? '启用' : '禁用' }}
                    </span>
                  </td>
                  <td>
                    <div class="table-actions">
                      <button v-if="canEditSku" class="btn btn-ghost btn-sm" type="button" @click="startEditSku(sku)">编辑</button>
                      <RouterLink class="btn btn-ghost btn-sm" :to="`/admin/inventory?skuId=${sku.id}`">库存</RouterLink>
                      <button v-if="canRemoveSku" class="link-danger" type="button" @click="removeSku(sku)">删除</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="admin-hint">暂无 SKU，商品无法上架。</p>

          <form v-if="skuEditing" class="sku-form" @submit.prevent="saveSku">
            <div class="admin-section-title">
              <h2>{{ skuForm.id ? '编辑 SKU' : '新增 SKU' }}</h2>
              <button class="text-link" type="button" @click="resetSkuForm">取消</button>
            </div>
            <div class="form-grid three">
              <label class="field">
                <span>SKU 编码 *</span>
                <input v-model="skuForm.skuCode" type="text" maxlength="64" :disabled="Boolean(skuForm.id)" />
              </label>
              <label class="field">
                <span>销售价 *</span>
                <input v-model="skuForm.price" type="number" min="0.01" step="0.01" />
              </label>
              <label class="field">
                <span>市场价</span>
                <input v-model="skuForm.marketPrice" type="number" min="0" step="0.01" />
              </label>
            </div>
            <div v-for="spec in specs" :key="spec.id" class="field">
              <span>{{ spec.name }} *</span>
              <select v-model="skuForm.selectedValueBySpec[spec.id]">
                <option value="">请选择{{ spec.name }}</option>
                <option v-for="value in spec.values" :key="value.id" :value="value.id">
                  {{ value.value }}
                </option>
              </select>
            </div>
            <div class="form-grid">
              <label class="field">
                <span>SKU 图片 URL</span>
                <input v-model="skuForm.imageUrl" type="url" maxlength="512" />
              </label>
              <label class="field">
                <span>状态</span>
                <select v-model="skuForm.status">
                  <option :value="1">启用</option>
                  <option :value="0">禁用</option>
                </select>
              </label>
            </div>
            <div class="form-actions">
              <button class="btn btn-primary" type="submit" :disabled="skuSaving">
                {{ skuSaving ? '保存中...' : '保存 SKU' }}
              </button>
              <button class="btn btn-ghost" type="button" @click="resetSkuForm">取消</button>
            </div>
          </form>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.form-grid.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.spec-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.spec-card {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface-muted);
}

.spec-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.spec-values {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
}

.spec-value {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--surface);
  border: 1px solid var(--border);
  font-size: 12px;
}

.spec-value button {
  border: 0;
  background: none;
  color: var(--text-muted);
  padding: 0 2px;
  font-size: 12px;
}

.spec-add-value {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.spec-add-value input {
  min-height: 32px;
  max-width: 220px;
}

.spec-add-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px auto;
  gap: 8px;
  margin-top: 14px;
}

.sku-form {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
}

@media (max-width: 760px) {
  .form-grid.three,
  .spec-add-form {
    grid-template-columns: 1fr;
  }
}
</style>
