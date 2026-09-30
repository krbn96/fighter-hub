<script setup lang="ts">
withDefaults(
  defineProps<{
    message?: string
  }>(),
  {
    message: 'Loading...',
  },
)
</script>

<template>
  <p class="loading-state">{{ message }}</p>
</template>

<style scoped>
.loading-state {
  color: var(--color-text-secondary);
  padding: var(--space-4) 0;
}
</style>
