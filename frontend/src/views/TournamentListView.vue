<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchTournaments } from '@/api/tournaments'
import { formatDateTime } from '@/utils/formatDateTime'
import type { Tournament } from '@/types/tournament'

const tournaments = ref<Tournament[]>([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  error.value = ''

  try {
    // APIから返された順序をそのまま表示する(フロント側でのsortはしない)
    tournaments.value = await fetchTournaments()
  } catch {
    error.value = '大会一覧の取得に失敗しました'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main>
    <h1>大会一覧</h1>

    <p v-if="loading">Loading...</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <p v-else-if="tournaments.length === 0">大会がありません</p>
    <ul v-else>
      <li v-for="tournament in tournaments" :key="tournament.id">
        <RouterLink :to="`/tournaments/${tournament.id}`">{{ tournament.name }}</RouterLink>
        <span> / {{ formatDateTime(tournament.startAt) }}</span>
        <span> / {{ tournament.status }}</span>
      </li>
    </ul>
  </main>
</template>
