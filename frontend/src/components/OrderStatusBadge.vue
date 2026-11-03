<script setup>
import { orderStatusClass, orderStatusText } from '../utils/format'

defineProps({
  status: { type: String, default: '' }
})
</script>

<template>
  <span class="status-badge" :class="orderStatusClass(status)">
    {{ orderStatusText(status) }}
  </span>
</template>
