<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingState from '../../components/LoadingState.vue'
import { adminProductApi } from '../../api/adminProduct'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const categories = ref([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const message = ref('')
const busyId = ref(null)
const showForm = ref(false)
const editingId = ref(null)

const form = reactive({ name: '', parentId: '', sort: 0, status: 1, iconUrl: '' })

const flatCategories = computed(() => {
  const result = []
  const walk = (nodes, depth) => {
    for (const node of nodes || []) {
      result.push({ ...node, depth })
      walk(node.children, depth + 1)
    }
  }
  walk(categories.value, 0)
  return result
})

const canCreate = computed(() => auth.hasPermission('product:category:create'))
const canUpdate = computed(() => auth.hasPermission('product:category:update'))
const canDelete = computed(() => auth.hasPermission('product:category:delete'))

async function loadCategories() {
  loading.value = true
  error.value = ''
  try {
    categories.value = await adminProductApi.categories()
  } catch (err) {
    error.value = err.message || '分类加载失败'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, { name: '', parentId: '', sort: 0, status: 1, iconUrl: '' })
  editingId.value = null
}

function openCreate() {
  resetForm()
  showForm.value = true
  message.value = ''
}

function openEdit(category) {
  editingId.value = category.id
  Object.assign(form, {
    name: category.name,
    parentId: category.parentId || '',
    sort: category.sort ?? 0,
    status: category.status ?? 1,
    iconUrl: category.iconUrl || ''
  })
  showForm.value = true
  message.value = ''
}

function closeForm() {
  showForm.value = false
  resetForm()
}

async function save() {
  error.value = ''
  if (!form.name.trim()) {
    error.value = '请填写分类名称'
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await adminProductApi.updateCategory(editingId.value, {
        name: form.name.trim(),
        sort: Number(form.sort) || 0,
        status: Number(form.status),
        iconUrl: form.iconUrl.trim() || null
      })
      message.value = '分类已更新'
    } else {
      await adminProductApi.createCategory({
        name: form.name.trim(),
        parentId: form.parentId ? Number(form.parentId) : 0,
        sort: Number(form.sort) || 0,
        status: Number(form.status),
        iconUrl: form.iconUrl.trim() || null
      })
      message.value = '分类已创建'
    }
    closeForm()
    await loadCategories()
  } catch (err) {
    error.value = err.message || '保存分类失败'
  } finally {
    saving.value = false
  }
}

async function removeCategory(category) {
  if (!window.confirm(`确认删除分类「${category.name}」吗？`)) return
  busyId.value = category.id
  error.value = ''
  try {
    await adminProductApi.deleteCategory(category.id)
    message.value = '分类已删除'
    await loadCategories()
  } catch (err) {
    error.value = err.message || '删除分类失败'
  } finally {
    busyId.value = null
  }
}

onMounted(loadCategories)
</script>

<template>
  <div>
    <div class="admin-page-heading">
      <div>
        <span class="eyebrow">Categories</span>
        <h1>分类管理</h1>
      </div>
      <button v-if="canCreate" class="btn btn-primary" type="button" @click="openCreate">新增分类</button>
    </div>

    <ErrorBanner :message="error" retryable @retry="loadCategories" />
    <div v-if="message" class="success-banner">{{ message }}</div>
    <div v-if="!canCreate && !canUpdate" class="admin-readonly-note">当前账号为只读权限，不能新增或修改分类。</div>

    <section v-if="showForm" class="admin-section-card">
      <div class="admin-section-title">
        <h2>{{ editingId ? '编辑分类' : '新增分类' }}</h2>
        <button class="btn btn-ghost btn-sm" type="button" @click="closeForm">关闭</button>
      </div>
      <form class="form-stack" @submit.prevent="save">
        <div class="form-grid">
          <label class="field">
            <span>分类名称 *</span>
            <input v-model="form.name" type="text" maxlength="64" />
          </label>
          <label class="field">
            <span>上级分类</span>
            <select v-model="form.parentId" :disabled="Boolean(editingId)">
              <option value="">顶级分类</option>
              <option v-for="item in flatCategories" :key="item.id" :value="item.id">
                {{ '　'.repeat(item.depth) }}{{ item.name }}
              </option>
            </select>
            <span v-if="editingId" class="admin-hint">父分类创建后不可修改</span>
          </label>
        </div>
        <div class="form-grid three">
          <label class="field">
            <span>排序</span>
            <input v-model="form.sort" type="number" />
          </label>
          <label class="field">
            <span>状态</span>
            <select v-model="form.status">
              <option :value="1">启用</option>
              <option :value="0">禁用</option>
            </select>
          </label>
          <label class="field">
            <span>图标 URL</span>
            <input v-model="form.iconUrl" type="url" maxlength="512" />
          </label>
        </div>
        <div class="form-actions">
          <button class="btn btn-primary" type="submit" :disabled="saving">
            {{ saving ? '保存中...' : '保存分类' }}
          </button>
          <button class="btn btn-ghost" type="button" @click="closeForm">取消</button>
        </div>
      </form>
    </section>

    <LoadingState v-if="loading" label="正在加载分类..." />

    <template v-else>
      <EmptyState v-if="!flatCategories.length" title="暂无分类" description="先创建分类，商品才能归类。">
        <button v-if="canCreate" class="btn btn-primary" type="button" @click="openCreate">新增分类</button>
      </EmptyState>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>分类</th>
              <th>层级</th>
              <th>排序</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="category in flatCategories" :key="category.id">
              <td>
                <strong>{{ '　'.repeat(category.depth) }}{{ category.name }}</strong>
                <div class="table-muted">ID {{ category.id }}</div>
              </td>
              <td class="table-muted">{{ category.depth + 1 }} 级</td>
              <td>{{ category.sort ?? '-' }}</td>
              <td>
                <span class="status-badge" :class="category.status === 1 ? 'status-on-shelf' : 'status-cancelled'">
                  {{ category.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td>
                <div class="table-actions">
                  <button v-if="canUpdate" class="btn btn-ghost btn-sm" type="button" @click="openEdit(category)">
                    编辑
                  </button>
                  <button
                    v-if="canDelete"
                    class="link-danger"
                    type="button"
                    :disabled="busyId === category.id"
                    @click="removeCategory(category)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
}

@media (max-width: 760px) {
  .form-grid.three {
    grid-template-columns: 1fr;
  }
}
</style>
