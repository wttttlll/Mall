<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false },
  showValue: { type: Boolean, default: false },
  size: { type: String, default: 'md' }
})
const emit = defineEmits(['update:modelValue'])

const stars = [1, 2, 3, 4, 5]
const displayValue = computed(() => {
  const value = Number(props.modelValue)
  return Number.isFinite(value) ? value : 0
})
const rounded = computed(() => Math.round(displayValue.value))

function choose(value) {
  if (!props.readonly) emit('update:modelValue', value)
}
</script>

<template>
  <span class="star-rating" :class="[size, { readonly }]" :aria-label="`评分 ${displayValue} 星`">
    <template v-for="star in stars" :key="star">
      <span v-if="readonly" class="star" :class="{ filled: star <= rounded }">★</span>
      <button
        v-else
        type="button"
        class="star star-button"
        :class="{ filled: star <= displayValue }"
        :aria-label="`${star} 星`"
        @click="choose(star)"
      >
        ★
      </button>
    </template>
    <span v-if="showValue" class="star-value">{{ displayValue.toFixed(1) }}</span>
  </span>
</template>

<style scoped>
.star-rating {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  color: #d1d5db;
  line-height: 1;
  vertical-align: middle;
}

.star {
  display: inline-block;
  font-size: 15px;
}

.star.filled {
  color: #f59e0b;
}

.star-button {
  padding: 2px;
  border: 0;
  background: transparent;
  color: #d1d5db;
  font-size: 22px;
  line-height: 1;
}

.star-button:hover,
.star-button.filled {
  color: #f59e0b;
}

.star-rating.lg .star {
  font-size: 25px;
}

.star-rating.sm .star {
  font-size: 12px;
}

.star-value {
  margin-left: 5px;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}
</style>
