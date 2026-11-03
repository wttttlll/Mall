<script setup>
defineProps({
  message: { type: String, default: '' },
  retryable: { type: Boolean, default: false }
})
defineEmits(['retry'])
</script>

<template>
  <div v-if="message" class="error-banner" role="alert">
    <span>{{ message }}</span>
    <button v-if="retryable" type="button" class="btn btn-ghost btn-sm" @click="$emit('retry')">
      重试
    </button>
  </div>
</template>
