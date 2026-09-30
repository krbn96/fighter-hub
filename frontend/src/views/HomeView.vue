<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchTournaments } from '@/api/tournaments'
import TournamentCard from '@/components/tournament/TournamentCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { Tournament } from '@/types/tournament'

// Homeでは一覧全件ではなく、先頭から一部だけをプレビューとして表示する。
// 「募集中」「開催予定」等、APIから保証できない意味は付けない。
const HOME_TOURNAMENT_PREVIEW_COUNT = 6

const tournaments = ref<Tournament[]>([])
const loading = ref(false)
const error = ref('')

async function loadTournaments() {
  loading.value = true
  error.value = ''

  try {
    const allTournaments = await fetchTournaments()
    tournaments.value = allTournaments.slice(0, HOME_TOURNAMENT_PREVIEW_COUNT)
  } catch {
    error.value = '大会情報の取得に失敗しました'
  } finally {
    loading.value = false
  }
}

onMounted(loadTournaments)
</script>

<template>
  <main class="home-view">
    <section class="home-view__hero">
      <div class="container home-view__hero-inner">
        <h1 class="home-view__headline">FIND YOUR TEAM.<br />ENTER THE FIGHT.</h1>
        <p class="home-view__subhead">大会に挑む仲間を見つけよう。</p>
        <BaseButton to="/tournaments">FIND TOURNAMENTS</BaseButton>
      </div>
    </section>

    <section class="home-view__tournaments container">
      <div class="home-view__section-header">
        <h2>Tournaments</h2>
        <RouterLink to="/tournaments" class="home-view__view-all">View All</RouterLink>
      </div>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTournaments" />
      <EmptyState v-else-if="tournaments.length === 0" message="大会がありません" />
      <div v-else class="home-view__grid">
        <TournamentCard
          v-for="tournament in tournaments"
          :key="tournament.id"
          :tournament="tournament"
        />
      </div>
    </section>
  </main>
</template>

<style scoped>
.home-view__hero {
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  padding: var(--space-12) 0;
  text-align: center;
}

.home-view__hero-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
}

.home-view__headline {
  font-size: 2rem;
  line-height: 1.15;
}

.home-view__subhead {
  color: var(--color-text-secondary);
  font-size: 1rem;
}

.home-view__tournaments {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.home-view__section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.home-view__view-all {
  font-size: 0.85rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.home-view__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

@media (min-width: 768px) {
  .home-view__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .home-view__grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .home-view__headline {
    font-size: 2.75rem;
  }
}
</style>
