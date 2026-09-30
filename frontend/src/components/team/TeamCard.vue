<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Team } from '@/types/team'

// TournamentCardと異なりmedia領域を持たない情報中心のカード。
// roleLabelはMyTeamViewでowner/memberを補助表示するための任意prop。
// TeamCard自身はAPIを呼ばず、渡されたteamのみを表示する。
defineProps<{
  team: Team
  roleLabel?: 'OWNER' | 'MEMBER'
}>()
</script>

<template>
  <RouterLink :to="`/teams/${team.id}`" class="team-card">
    <div class="team-card__header">
      <h3 class="team-card__name">{{ team.name }}</h3>
      <span
        v-if="roleLabel"
        class="team-card__role"
        :class="`team-card__role--${roleLabel.toLowerCase()}`"
        >{{ roleLabel }}</span
      >
    </div>

    <p class="team-card__tournament">{{ team.tournamentName }}</p>

    <div class="team-card__meta">
      <span class="team-card__meta-item">Owner: {{ team.ownerName }}</span>
      <span class="team-card__meta-item"
        >募集ランク: {{ team.rankRequirement ?? '指定なし' }}</span
      >
    </div>

    <p class="team-card__message">{{ team.recruitmentMessage ?? '募集メッセージはありません' }}</p>
  </RouterLink>
</template>

<style scoped>
.team-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  color: var(--color-text);
  text-decoration: none;
  transition:
    border-color var(--transition-fast),
    background-color var(--transition-fast);
}

.team-card:hover {
  border-color: var(--color-primary);
  background: var(--color-surface-hover);
}

.team-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-2);
}

.team-card__name {
  font-size: 1rem;
  margin: 0;
}

.team-card__role {
  flex-shrink: 0;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.team-card__role--owner {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.team-card__tournament {
  margin: 0;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.team-card__meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: 0.8rem;
  color: var(--color-text-secondary);
}

.team-card__message {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
