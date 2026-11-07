<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  active: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  showLabel: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  activeLabel: { type: String, default: '已收藏' },
  inactiveLabel: { type: String, default: '收藏' }
})
const emit = defineEmits(['toggle'])
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const label = computed(() => (props.active ? props.activeLabel : props.inactiveLabel))

function toggle(event) {
  event.preventDefault()
  event.stopPropagation()
  if (props.busy) return
  if (!auth.isAuthenticated) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  emit('toggle', !props.active)
}
</script>

<template>
  <button
    type="button"
    class="favorite-btn"
    :class="{ active, busy, compact, labeled: showLabel }"
    :disabled="busy"
    :aria-pressed="active"
    :aria-label="label"
    :title="label"
    @click="toggle"
  >
    <span class="heart" aria-hidden="true">{{ active ? '♥' : '♡' }}</span>
    <span v-if="showLabel" class="favorite-label">{{ busy ? '处理中...' : label }}</span>
  </button>
</template>

<style scoped>
.favorite-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--text-muted);
  box-shadow: var(--shadow-sm);
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.favorite-btn:hover {
  color: var(--price);
  border-color: #fecdd3;
  background: #fff7f8;
  transform: translateY(-1px);
}

.favorite-btn.active {
  color: var(--price);
  border-color: #fecdd3;
  background: #fff1f2;
}

.favorite-btn.compact {
  width: 30px;
  height: 30px;
  font-size: 13px;
}

.favorite-btn.labeled {
  width: auto;
  padding: 0 13px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
}

.heart {
  font-size: 17px;
  line-height: 1;
}

.favorite-btn.compact .heart {
  font-size: 14px;
}

.favorite-btn.labeled .heart {
  font-size: 16px;
}
</style>
