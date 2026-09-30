<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchTournaments } from '@/api/tournaments'
import TournamentCard from '@/components/tournament/TournamentCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { Tournament } from '@/types/tournament'

const tournaments = ref<Tournament[]>([])
const loading = ref(false)
const error = ref('')

async function loadTournaments() {
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
}

onMounted(loadTournaments)
</script>

<template>
  <main class="tournament-list-view">
    <div class="container">
      <header class="tournament-list-view__header">
        <h1>Tournaments</h1>
        <p>参加する大会を選んで、チームを作る・探す。</p>
      </header>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTournaments" />
      <EmptyState v-else-if="tournaments.length === 0" message="大会がありません" />
      <div v-else class="tournament-list-view__grid">
        <TournamentCard
          v-for="tournament in tournaments"
          :key="tournament.id"
          :tournament="tournament"
        />
      </div>
    </div>
  </main>
</template>

<style scoped>
.tournament-list-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.tournament-list-view__header {
  margin-bottom: var(--space-6);
}

.tournament-list-view__header p {
  color: var(--color-text-secondary);
  margin-top: var(--space-2);
}

.tournament-list-view__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

@media (min-width: 768px) {
  .tournament-list-view__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .tournament-list-view__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
