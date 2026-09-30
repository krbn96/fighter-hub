<script setup lang="ts">
// retryable=trueかつ@retryを購読すれば再試行ボタンを出せる構造にしておく。
// 現時点ではどのViewからも利用していない(将来のPhaseでの置き換え用)。
withDefaults(
  defineProps<{
    message: string
    retryable?: boolean
  }>(),
  {
    retryable: false,
  },
)

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="error-state" role="alert">
    <p class="error-state__message">{{ message }}</p>
    <button v-if="retryable" type="button" class="error-state__retry" @click="emit('retry')">
      再試行
    </button>
  </div>
</template>

<style scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-4);
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid var(--color-error);
  border-radius: var(--radius-md);
  color: var(--color-error);
}

.error-state__retry {
  background: transparent;
  border: 1px solid var(--color-error);
  color: var(--color-error);
  border-radius: var(--radius-sm);
  padding: var(--space-1) var(--space-3);
  font-size: 0.85rem;
  font-family: inherit;
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    color var(--transition-fast);
}

.error-state__retry:hover {
  background: var(--color-error);
  color: var(--color-text);
}
</style>
