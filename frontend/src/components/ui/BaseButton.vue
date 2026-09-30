<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

// buttonとRouterLinkの両方を、見た目だけ揃えて使えるようにする。
// toを渡せばRouterLink、渡さなければ通常のbuttonとして描画する。
const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger'
    to?: string
    type?: 'button' | 'submit'
    disabled?: boolean
  }>(),
  {
    variant: 'primary',
    to: undefined,
    type: 'button',
    disabled: false,
  },
)

const classes = computed(() => ['base-button', `base-button--${props.variant}`])
</script>

<template>
  <RouterLink v-if="to" :to="to" :class="classes"><slot /></RouterLink>
  <button v-else :type="type" :class="classes" :disabled="disabled"><slot /></button>
</template>

<style scoped>
.base-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  font-family: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  cursor: pointer;
  text-decoration: none;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.base-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.base-button--primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-text);
}

.base-button--primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: var(--color-primary-hover);
}

.base-button--secondary {
  background: transparent;
  border-color: var(--color-border);
  color: var(--color-text);
}

.base-button--secondary:hover:not(:disabled) {
  background: var(--color-surface-hover);
}

.base-button--danger {
  background: transparent;
  border-color: var(--color-error);
  color: var(--color-error);
}

.base-button--danger:hover:not(:disabled) {
  background: var(--color-error);
  color: var(--color-text);
}
</style>
