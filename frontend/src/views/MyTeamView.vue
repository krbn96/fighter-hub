<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchMyTeams } from '@/api/teams'
import { useAuthStore } from '@/stores/auth'
import type { Team } from '@/types/team'

const authStore = useAuthStore()

const teams = ref<Team[]>([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  error.value = ''

  try {
    // APIが返した順序をそのまま表示する(フロント側でのsortはしない)
    teams.value = await fetchMyTeams()
  } catch {
    error.value = '所属チームの取得に失敗しました'
  } finally {
    loading.value = false
  }
})

function roleLabel(team: Team): string {
  return team.ownerId === authStore.user?.id ? 'owner' : 'member'
}
</script>

<template>
  <main>
    <h1>所属チーム一覧</h1>

    <p v-if="loading">Loading...</p>
    <p v-else-if="error" role="alert">{{ error }}</p>
    <p v-else-if="teams.length === 0">所属しているチームはありません</p>
    <ul v-else>
      <li v-for="team in teams" :key="team.id">
        <RouterLink :to="`/teams/${team.id}`">{{ team.name }}</RouterLink>
        <span> / 大会: {{ team.tournamentName }}</span>
        <span> / owner: {{ team.ownerName }}</span>
        <span> / 自分の立場: {{ roleLabel(team) }}</span>
      </li>
    </ul>
  </main>
</template>
