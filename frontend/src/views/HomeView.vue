<script setup lang="ts">
import { onMounted, ref } from 'vue'
import TheWelcome from '../components/TheWelcome.vue'
import { apiClient } from '@/api/client'
import type { Character } from '@/types/character'

// Week 7 Day 1: Vue -> Spring Boot API疎通確認のための最小実装。
// キャラクター一覧を取得できるかどうかだけを確認する。
const characters = ref<Character[]>([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  error.value = ''

  try {
    const response = await apiClient.get<Character[]>('/characters')
    characters.value = response.data
  } catch {
    error.value = 'キャラクター一覧の取得に失敗しました'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main>
    <section>
      <h2>Character API 疎通確認</h2>
      <p v-if="loading">Loading...</p>
      <p v-else-if="error">{{ error }}</p>
      <ul v-else>
        <li v-for="character in characters" :key="character.id">
          {{ character.id }}: {{ character.name }}
        </li>
      </ul>
    </section>

    <TheWelcome />
  </main>
</template>
