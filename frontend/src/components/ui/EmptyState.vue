<script setup lang="ts">
// CTAはslotで渡す(例: <EmptyState message="...">  <BaseButton to="/tournaments">...</BaseButton> </EmptyState>)。
// 現時点ではどのViewからも利用していない(将来のPhaseでの置き換え用)。
defineProps<{
  message: string
}>()
</script>

<template>
  <div class="empty-state">
    <p class="empty-state__message">{{ message }}</p>
    <div v-if="$slots.default" class="empty-state__action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-6) var(--space-4);
  color: var(--color-text-secondary);
}
</style>
