<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { fetchTournamentById } from '@/api/tournaments'
import { formatDateTime } from '@/utils/formatDateTime'
import { formatTeamSize } from '@/utils/formatTeamSize'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { Tournament } from '@/types/tournament'

const route = useRoute()

const tournament = ref<Tournament | null>(null)
const loading = ref(false)
const error = ref('')
const notFound = ref(false)

async function loadTournament() {
  loading.value = true
  error.value = ''
  notFound.value = false

  try {
    const id = String(route.params.id)
    tournament.value = await fetchTournamentById(id)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      error.value = '大会情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadTournament)
</script>

<template>
  <main class="tournament-detail-view">
    <div class="container">
      <p class="tournament-detail-view__back">
        <RouterLink to="/tournaments">&larr; 大会一覧へ戻る</RouterLink>
      </p>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定した大会が見つかりません" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTournament" />

      <template v-else-if="tournament">
        <header class="tournament-detail-view__header">
          <h1>{{ tournament.name }}</h1>
          <StatusBadge :status="tournament.status" />
        </header>

        <div class="tournament-detail-view__meta">
          <div class="tournament-detail-view__meta-item">
            <span class="tournament-detail-view__meta-label">Start</span>
            <span class="tournament-detail-view__meta-value">{{
              formatDateTime(tournament.startAt)
            }}</span>
          </div>
          <div class="tournament-detail-view__meta-item">
            <span class="tournament-detail-view__meta-label">Team Size</span>
            <span class="tournament-detail-view__meta-value">{{
              formatTeamSize(tournament.teamSize)
            }}</span>
          </div>
          <div class="tournament-detail-view__meta-item">
            <span class="tournament-detail-view__meta-label">Max Players</span>
            <span class="tournament-detail-view__meta-value">{{ tournament.maxPlayers }}</span>
          </div>
        </div>

        <section class="tournament-detail-view__actions">
          <h2>参加する</h2>
          <div class="tournament-detail-view__action-grid">
            <BaseCard class="tournament-detail-view__action-card">
              <h3>チームを作る</h3>
              <p>新しいチームを作って、メンバーを募集する。</p>
              <BaseButton :to="`/tournaments/${tournament.id}/teams/create`"
                >CREATE TEAM</BaseButton
              >
            </BaseCard>
            <BaseCard class="tournament-detail-view__action-card">
              <h3>チームを探す</h3>
              <p>募集中のチームを探して、参加申請する。</p>
              <BaseButton variant="secondary" :to="`/tournaments/${tournament.id}/teams`"
                >FIND A TEAM</BaseButton
              >
            </BaseCard>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.tournament-detail-view {
  padding-top: var(--space-6);
  padding-bottom: var(--space-12);
}

.tournament-detail-view__back {
  margin-bottom: var(--space-4);
  font-size: 0.85rem;
}

.tournament-detail-view__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}

.tournament-detail-view__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-6);
  padding: var(--space-4);
  margin-bottom: var(--space-8);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.tournament-detail-view__meta-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tournament-detail-view__meta-label {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tournament-detail-view__meta-value {
  font-weight: 700;
}

.tournament-detail-view__actions h2 {
  margin-bottom: var(--space-4);
}

.tournament-detail-view__action-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

.tournament-detail-view__action-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}

.tournament-detail-view__action-card p {
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

@media (min-width: 768px) {
  .tournament-detail-view__action-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
