<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { fetchTournamentById, updateTournament } from '@/api/tournaments'
import TournamentForm from '@/components/tournament/TournamentForm.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import type { Tournament, TournamentFormValues, TournamentUpdateRequest } from '@/types/tournament'

const route = useRoute()
const router = useRouter()

const tournamentId = String(route.params.id)

const tournament = ref<Tournament | null>(null)
const loading = ref(false)
const loadError = ref('')
const notFound = ref(false)

const submitting = ref(false)
const submitError = ref('')
const forbidden = ref(false)

async function loadTournament() {
  loading.value = true
  loadError.value = ''
  notFound.value = false

  try {
    tournament.value = await fetchTournamentById(tournamentId)
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      loadError.value = '大会情報の取得に失敗しました'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadTournament)

async function handleSubmit(values: TournamentFormValues) {
  submitting.value = true
  submitError.value = ''
  forbidden.value = false

  const request: TournamentUpdateRequest = values

  try {
    tournament.value = await updateTournament(tournamentId, request)
    router.push('/admin/tournaments')
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 400) {
      submitError.value = '入力内容を確認してください'
    } else if (axios.isAxiosError(e) && e.response?.status === 403) {
      forbidden.value = true
    } else if (axios.isAxiosError(e) && e.response?.status === 404) {
      notFound.value = true
    } else {
      submitError.value = '大会の更新に失敗しました'
    }
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.push('/admin/tournaments')
}
</script>

<template>
  <main class="admin-tournament-edit-view">
    <div class="container">
      <LoadingState v-if="loading" />
      <ErrorState v-else-if="notFound" message="指定した大会が見つかりません" />
      <ErrorState v-else-if="forbidden" message="ADMIN権限が必要です" />
      <ErrorState v-else-if="loadError" :message="loadError" retryable @retry="loadTournament" />

      <template v-else-if="tournament">
        <header class="admin-tournament-edit-view__header">
          <h1>EDIT TOURNAMENT</h1>
          <p>大会情報を更新する(ADMIN専用)。</p>
        </header>

        <TournamentForm
          :initial-values="{
            name: tournament.name,
            teamSize: tournament.teamSize,
            maxPlayers: tournament.maxPlayers,
            startAt: tournament.startAt.slice(0, 16),
            recruitmentDeadline: tournament.recruitmentDeadline.slice(0, 16),
          }"
          :submitting="submitting"
          submit-label="SAVE CHANGES"
          submitting-label="SAVING..."
          :error="submitError"
          @submit="handleSubmit"
          @cancel="handleCancel"
        />
      </template>
    </div>
  </main>
</template>

<style scoped>
.admin-tournament-edit-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.admin-tournament-edit-view__header {
  max-width: 640px;
  margin: 0 auto var(--space-6);
  text-align: center;
}

.admin-tournament-edit-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}
</style>
