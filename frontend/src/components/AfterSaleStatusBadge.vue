<script setup>
import { afterSaleStatusClass, afterSaleStatusText } from '../utils/format'

defineProps({
  status: { type: String, default: '' }
})
</script>

<template>
  <span class="status-badge" :class="afterSaleStatusClass(status)">
    {{ afterSaleStatusText(status) }}
  </span>
</template>
