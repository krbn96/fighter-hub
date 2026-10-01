<script setup lang="ts">
import { computed } from 'vue'

type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'error'

// 特定のstatus文字列に強く依存しないよう、既知の値には既定のtoneを与えつつ、
// 呼び出し側がtoneを明示的に渡せば任意のstatus文字列でも使えるようにする。
const DEFAULT_TONE_BY_STATUS: Record<string, Tone> = {
  UPCOMING: 'primary',
  OPEN: 'primary',
  RECRUITING: 'primary',
  CLOSED: 'neutral',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
}

const props = withDefaults(
  defineProps<{
    status: string
    tone?: Tone
  }>(),
  {
    tone: undefined,
  },
)

const resolvedTone = computed<Tone>(() => props.tone ?? DEFAULT_TONE_BY_STATUS[props.status] ?? 'neutral')
</script>

<template>
  <span class="status-badge" :class="`status-badge--${resolvedTone}`">{{ status }}</span>
</template>

<style scoped>
.status-badge {
  display: inline-block;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  border: 1px solid currentColor;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.status-badge--neutral {
  color: var(--color-text-secondary);
}

.status-badge--primary {
  color: var(--color-primary);
}

.status-badge--success {
  color: var(--color-success);
}

.status-badge--warning {
  color: var(--color-warning);
}

.status-badge--error {
  color: var(--color-error);
}
</style>
