<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

// Day 2は認証確認用の最小画面。プロフィール編集機能はまだ実装しない。
const authStore = useAuthStore()
</script>

<template>
  <main>
    <h1>My Page</h1>
    <div v-if="authStore.user">
      <p>名前: {{ authStore.user.name }}</p>
      <p>メールアドレス: {{ authStore.user.email }}</p>
      <h2>使用キャラクター</h2>
      <ul>
        <li v-for="character in authStore.user.characters" :key="character.characterId">
          キャラクターID: {{ character.characterId }} / ランク: {{ character.rank }}
        </li>
      </ul>
    </div>
  </main>
</template>
