<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

// 既存コードベースにModal/Dialogパターンが無かったため新設した、汎用的な確認/情報モーダルの
// 外枠(backdrop+dialog本体+title)のみを提供するコンポーネント。本文・フッターボタンは
// 呼び出し側がslotで渡す(過剰な設定オプションは持たせない最小限の実装)。
defineProps<{
  title: string
}>()

const emit = defineEmits<{
  close: []
}>()

let modalIdCounter = 0
const titleId = `base-modal-title-${++modalIdCounter}`

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="base-modal__backdrop" @click="emit('close')">
      <div
        class="base-modal"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        @click.stop
      >
        <h2 :id="titleId" class="base-modal__title">{{ title }}</h2>
        <div class="base-modal__body">
          <slot />
        </div>
        <div class="base-modal__actions">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.base-modal__backdrop {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  background: rgba(0, 0, 0, 0.5);
  z-index: 100;
}

.base-modal {
  width: 100%;
  max-width: 420px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.base-modal__title {
  margin: 0;
}

.base-modal__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.base-modal__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-3);
}
</style>
