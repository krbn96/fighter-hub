<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTeamsByTournament } from '@/api/teams'
import TeamCard from '@/components/team/TeamCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { Team } from '@/types/team'

const route = useRoute()

const teams = ref<Team[]>([])
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

async function loadTeams() {
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
}

onMounted(loadTeams)
</script>

<template>
  <main class="tournament-teams-view">
    <div class="container">
      <header class="tournament-teams-view__header">
        <h1>募集チーム一覧</h1>
      </header>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定した大会が見つかりません" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTeams" />
      <EmptyState v-else-if="teams.length === 0" message="この大会にはまだチームがありません" />
      <div v-else class="tournament-teams-view__grid">
        <TeamCard v-for="team in teams" :key="team.id" :team="team" />
      </div>

      <p class="tournament-teams-view__back">
        <RouterLink :to="`/tournaments/${route.params.id}`">大会詳細へ戻る</RouterLink>
      </p>
    </div>
  </main>
</template>

<style scoped>
.tournament-teams-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.tournament-teams-view__header {
  margin-bottom: var(--space-6);
}

.tournament-teams-view__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

@media (min-width: 768px) {
  .tournament-teams-view__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .tournament-teams-view__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.tournament-teams-view__back {
  margin-top: var(--space-8);
  font-size: 0.85rem;
}
</style>
