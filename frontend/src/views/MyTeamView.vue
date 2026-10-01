<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchMyTeams } from '@/api/teams'
import { useAuthStore } from '@/stores/auth'
import TeamCard from '@/components/team/TeamCard.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import type { Team } from '@/types/team'

const authStore = useAuthStore()

const teams = ref<Team[]>([])
const loading = ref(false)
const error = ref('')

async function loadTeams() {
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
}

onMounted(loadTeams)

function roleLabel(team: Team): 'OWNER' | 'MEMBER' {
  return team.ownerId === authStore.user?.id ? 'OWNER' : 'MEMBER'
}
</script>

<template>
  <main class="my-team-view">
    <div class="container">
      <header class="my-team-view__header">
        <h1>MY TEAMS</h1>
      </header>

      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" retryable @retry="loadTeams" />
      <EmptyState v-else-if="teams.length === 0" message="所属しているチームはありません" />
      <div v-else class="my-team-view__grid">
        <TeamCard
          v-for="team in teams"
          :key="team.id"
          :team="team"
          :role-label="roleLabel(team)"
        />
      </div>
    </div>
  </main>
</template>

<style scoped>
.my-team-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.my-team-view__header {
  margin-bottom: var(--space-6);
}

.my-team-view__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-4);
}

@media (min-width: 768px) {
  .my-team-view__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .my-team-view__grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
