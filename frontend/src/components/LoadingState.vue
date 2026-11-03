<script setup>
defineProps({
  label: { type: String, default: '加载中...' }
})
</script>

<template>
  <div class="loading-state" role="status">
    <span class="spinner" aria-hidden="true"></span>
    <span>{{ label }}</span>
  </div>
</template>
