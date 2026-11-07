<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatCountdown, remainingMs } from '../utils/orderTime'

const props = defineProps({
  deadline: { type: String, default: '' },
  active: { type: Boolean, default: false }
})
const emit = defineEmits(['expired'])

const now = ref(Date.now())
let timer = null
let lastExpiredEmit = 0

const remaining = computed(() => remainingMs(props.deadline, now.value))
const expired = computed(
  () => props.active && remaining.value !== null && remaining.value <= 0
)
const text = computed(() => {
  if (!props.active || remaining.value === null) return ''
  return expired.value
    ? '已超时，等待系统取消'
    : `剩余支付时间 ${formatCountdown(remaining.value)}`
})

function emitExpiredIfNeeded() {
  if (!expired.value) return
  const current = Date.now()
  if (current - lastExpiredEmit >= 10_000) {
    lastExpiredEmit = current
    emit('expired')
  }
}

watch([remaining, () => props.active], emitExpiredIfNeeded)

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
  emitExpiredIfNeeded()
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <span v-if="text" class="order-countdown" :class="{ expired }">
    {{ text }}
  </span>
</template>
