<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchMyApplications } from '@/api/applications'
import { fetchTeamById } from '@/api/teams'
import BaseCard from '@/components/ui/BaseCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { formatDateTime } from '@/utils/formatDateTime'
import type { RecruitmentApplication } from '@/types/recruitmentApplication'
import type { Team } from '@/types/team'

interface ApplicationWithTeam {
  application: RecruitmentApplication
  team: Team | null
}

const applications = ref<ApplicationWithTeam[]>([])
const loading = ref(false)
const error = ref('')

async function loadApplications() {
  loading.value = true
  error.value = ''

  try {
    const myApplications = await fetchMyApplications()

    // APIレスポンスにはteamIdしか無いため、fetchTeamByIdでTeam情報を補完する。
    // 同じteamIdを重複して取得しないよう、ユニークなteamIdだけをMapへキャッシュする。
    const uniqueTeamIds = [...new Set(myApplications.map((application) => application.teamId))]
    const teamEntries = await Promise.all(
      uniqueTeamIds.map(async (teamId): Promise<[number, Team | null]> => {
        try {
          const team = await fetchTeamById(String(teamId))
          return [teamId, team]
        } catch {
          return [teamId, null]
        }
      }),
    )
    const teamMap = new Map<number, Team | null>(teamEntries)

    applications.value = myApplications.map((application) => ({
      application,
      team: teamMap.get(application.teamId) ?? null,
    }))
  } catch {
    error.value = '参加申請の取得に失敗しました'
  } finally {
    loading.value = false
  }
}

onMounted(loadApplications)
</script>

<template>
  <main class="my-application-view">
    <div class="container">
      <header class="my-application-view__header">
        <h1>MY APPLICATIONS</h1>
      </header>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadApplications" />
      <EmptyState v-else-if="applications.length === 0" message="参加申請はありません" />
      <div v-else class="my-application-view__list">
        <BaseCard
          v-for="item in applications"
          :key="item.application.id"
          class="my-application-view__item"
        >
          <div class="my-application-view__item-header">
            <RouterLink
              :to="`/teams/${item.application.teamId}`"
              class="my-application-view__team-name"
            >
              {{ item.team ? item.team.name : `Team #${item.application.teamId}` }}
            </RouterLink>
            <StatusBadge :status="item.application.status" />
          </div>
          <p v-if="item.team" class="my-application-view__tournament">
            大会: {{ item.team.tournamentName }}
          </p>
          <p class="my-application-view__message">
            {{ item.application.message ?? '(メッセージなし)' }}
          </p>
          <p class="my-application-view__date">
            申請日時: {{ formatDateTime(item.application.createdAt) }}
          </p>
        </BaseCard>
      </div>
    </div>
  </main>
</template>

<style scoped>
.my-application-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.my-application-view__header {
  margin-bottom: var(--space-6);
}

.my-application-view__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.my-application-view__item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.my-application-view__item-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.my-application-view__team-name {
  font-weight: 700;
  color: var(--color-text);
  text-decoration: none;
}

.my-application-view__team-name:hover,
.my-application-view__team-name:focus-visible {
  color: var(--color-primary);
  text-decoration: underline;
}

.my-application-view__tournament {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.85rem;
}

.my-application-view__message {
  margin: 0;
  color: var(--color-text);
  white-space: pre-wrap;
}

.my-application-view__date {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 0.8rem;
}
</style>
