<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTeamsByTournament } from '@/api/teams'
import type { Team } from '@/types/team'

const route = useRoute()

const teams = ref<Team[]>([])
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

onMounted(async () => {
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const tournamentId = String(route.params.id)
    // APIレスポンスの順序をそのまま表示する(フロント側でのsortはしない)
    teams.value = await fetchTeamsByTournament(tournamentId)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      error.value = 'チーム一覧の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main>
    <h1>募集チーム一覧</h1>

    <p v-if="loading">Loading...</p>
    <p v-else-if="notFound" role="alert">指定した大会が見つかりません</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <p v-else-if="teams.length === 0">募集中のチームがありません</p>
    <ul v-else>
      <li v-for="team in teams" :key="team.id">
        <RouterLink :to="`/teams/${team.id}`">{{ team.name }}</RouterLink>
        <span> / owner: {{ team.ownerName }}</span>
        <span> / 募集ランク: {{ team.rankRequirement ?? '指定なし' }}</span>
      </li>
    </ul>

    <p><RouterLink :to="`/tournaments/${route.params.id}`">大会詳細へ戻る</RouterLink></p>
  </main>
</template>
