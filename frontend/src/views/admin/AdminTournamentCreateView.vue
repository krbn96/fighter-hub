<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { createTournament } from '@/api/tournaments'
import TournamentForm from '@/components/tournament/TournamentForm.vue'
import type { TournamentCreateRequest, TournamentFormValues } from '@/types/tournament'

const router = useRouter()

const submitting = ref(false)
const error = ref('')

async function handleSubmit(values: TournamentFormValues) {
  submitting.value = true
  error.value = ''

  const request: TournamentCreateRequest = values

  try {
    await createTournament(request)
    router.push('/admin/tournaments')
  } catch (e) {
    if (axios.isAxiosError(e) && e.response?.status === 400) {
      error.value = '入力内容を確認してください'
    } else if (axios.isAxiosError(e) && e.response?.status === 403) {
      error.value = 'ADMIN権限が必要です'
    } else {
      error.value = '大会の作成に失敗しました'
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
  <main class="admin-tournament-create-view">
    <div class="container">
      <header class="admin-tournament-create-view__header">
        <h1>CREATE TOURNAMENT</h1>
        <p>新しい大会を作成する(ADMIN専用)。</p>
      </header>

      <TournamentForm
        :submitting="submitting"
        submit-label="CREATE TOURNAMENT"
        submitting-label="CREATING..."
        :error="error"
        @submit="handleSubmit"
        @cancel="handleCancel"
      />
    </div>
  </main>
</template>

<style scoped>
.admin-tournament-create-view {
  padding-top: var(--space-8);
  padding-bottom: var(--space-12);
}

.admin-tournament-create-view__header {
  max-width: 640px;
  margin: 0 auto var(--space-6);
  text-align: center;
}

.admin-tournament-create-view__header p {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
}
</style>
