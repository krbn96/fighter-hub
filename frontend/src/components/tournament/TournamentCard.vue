<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { formatDateTime } from '@/utils/formatDateTime'
import { formatTeamSize } from '@/utils/formatTeamSize'
import { getRecruitmentStatus } from '@/utils/recruitmentStatus'
import type { Tournament } from '@/types/tournament'

const props = defineProps<{
  tournament: Tournament
}>()

// 募集状態はbackendのstatusではなくrecruitmentDeadlineから導出する。
const recruitmentStatus = computed(() => getRecruitmentStatus(props.tournament.recruitmentDeadline))
</script>

<template>
  <RouterLink :to="`/tournaments/${tournament.id}`" class="tournament-card">
    <div class="tournament-card__body">
      <div class="tournament-card__header">
        <h3 class="tournament-card__name">{{ tournament.name }}</h3>
        <StatusBadge :status="recruitmentStatus" />
      </div>

      <div class="tournament-card__meta">
        <span>{{ formatTeamSize(tournament.teamSize) }}</span>
        <span>DEADLINE {{ formatDateTime(tournament.recruitmentDeadline) }}</span>
        <span>START {{ formatDateTime(tournament.startAt) }}</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.tournament-card {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  color: var(--color-text);
  text-decoration: none;
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast);
}

.tournament-card:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.tournament-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  flex: 1;
}

.tournament-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
}

.tournament-card__name {
  font-size: 1rem;
  margin: 0;
}

.tournament-card__meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}
</style>
