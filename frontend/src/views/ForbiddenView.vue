<script setup lang="ts">
import BaseButton from '@/components/ui/BaseButton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'

// Vue RouterのrequiresAdminガード(router/index.ts)が、ログイン済みだがADMINではない
// ユーザーのアクセスをここへ遷移させる(未認証の場合は/loginへ遷移させ、ここへは来ない)。
</script>

<template>
  <main class="forbidden-view">
    <div class="container">
      <ErrorState message="このページを表示する権限がありません(ADMIN専用ページです)。" />
      <BaseButton to="/" class="forbidden-view__home-link">トップページへ戻る</BaseButton>
    </div>
  </main>
</template>

<style scoped>
.forbidden-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.forbidden-view__home-link {
  margin-top: var(--space-4);
}
</style>
